/*
 * Copyright (c) 2026. The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlesource.gerrit.plugins.reviewai.aibackend.common.client.api.gerrit;

import com.googlesource.gerrit.plugins.reviewai.aibackend.common.model.api.gerrit.GerritComment;
import com.googlesource.gerrit.plugins.reviewai.config.Configuration;
import com.googlesource.gerrit.plugins.reviewai.localization.Localizer;
import com.googlesource.gerrit.plugins.reviewai.web.model.AiReviewHistoryInfo;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GerritAiReviewHistoryCollectorTest {
  @Test
  public void collectsOnlyUserMessagesDirectedToAi() {
    Configuration config = mock(Configuration.class);
    when(config.getGerritUserName()).thenReturn("reviewai");
    when(config.getGerritUserEmail()).thenReturn("");

    Localizer localizer = mock(Localizer.class);
    when(localizer.getText("system.message.prefix")).thenReturn("System");

    GerritAiReviewHistoryCollector collector = new GerritAiReviewHistoryCollector();

    GerritComment patchSetPrompt =
        newComment(
            "msg-1",
            1001,
            "Alice",
            "Patch Set 5:\n\n@reviewai please verify the null handling.",
            "2026-04-09 10:00:00.000000",
            5,
            null,
            null);
    GerritComment botReply =
        newComment(
            "msg-2",
            7,
            "ReviewAI",
            "This is the AI review output.",
            "2026-04-09 10:01:00.000000",
            5,
            null,
            null);
    GerritComment inlinePrompt =
        newComment(
            "c-1",
            1002,
            "Bob",
            "@reviewai can you explain this branch?",
            "2026-04-09 10:02:00.000000",
            5,
            "src/main/java/Foo.java",
            42);
    GerritComment inlineAiReply =
        newComment(
            "c-1-r1",
            7,
            "ReviewAI",
            "This branch is guarding the fallback path.",
            "2026-04-09 10:02:30.000000",
            5,
            "src/main/java/Foo.java",
            42);
    inlineAiReply.setInReplyTo("c-1");
    GerritComment nonAddressedInline =
        newComment(
            "c-2",
            1003,
            "Carol",
            "I think this is fine.",
            "2026-04-09 10:03:00.000000",
            5,
            "src/main/java/Foo.java",
            43);
    GerritComment commandOnly =
        newComment(
            "msg-3",
            1004,
            "Dave",
            "Patch Set 5:\n\n@reviewai /review",
            "2026-04-09 10:04:00.000000",
            5,
            null,
            null);

    AiReviewHistoryInfo info =
        collector.collect(
            config,
            localizer,
            7,
            Map.of(
                "/PATCHSET_LEVEL", List.of(patchSetPrompt, botReply, commandOnly),
                "src/main/java/Foo.java", List.of(inlinePrompt, inlineAiReply, nonAddressedInline)));

    assertEquals(3, info.getEntries().size());

    AiReviewHistoryInfo.Entry first = info.getEntries().get(0);
    assertEquals("Alice", first.getAuthor());
    assertEquals("user", first.getRole());
    assertEquals("please verify the null handling.", first.getMessage());
    assertNull(first.getFilename());

    AiReviewHistoryInfo.Entry second = info.getEntries().get(1);
    assertEquals("Bob", second.getAuthor());
    assertEquals("user", second.getRole());
    assertEquals("can you explain this branch?", second.getMessage());
    assertEquals("src/main/java/Foo.java", second.getFilename());
    assertEquals(Integer.valueOf(42), second.getLine());

    AiReviewHistoryInfo.Entry third = info.getEntries().get(2);
    assertEquals("ReviewAI", third.getAuthor());
    assertEquals("assistant", third.getRole());
    assertEquals("This branch is guarding the fallback path.", third.getMessage());
  }

  private static GerritComment newComment(
      String id,
      int accountId,
      String authorName,
      String message,
      String updated,
      Integer patchSet,
      String filename,
      Integer line) {
    GerritComment comment = new GerritComment();
    GerritComment.Author author = new GerritComment.Author();
    author.setAccountId(accountId);
    author.setName(authorName);
    author.setUsername(authorName.toLowerCase());
    comment.setAuthor(author);
    comment.setId(id);
    comment.setMessage(message);
    comment.setUpdated(updated);
    comment.setPatchSet(patchSet);
    comment.setFilename(filename);
    comment.setLine(line);
    return comment;
  }
}
