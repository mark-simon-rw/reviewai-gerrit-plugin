/*
 * Copyright (c) 2025. The Android Open Source Project
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

package com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.openai;

import com.googlesource.gerrit.plugins.reviewai.aibackend.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.OpenAiUriResourceLocator;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.model.api.openai.OpenAiResponse;
import com.googlesource.gerrit.plugins.reviewai.config.Configuration;
import com.googlesource.gerrit.plugins.reviewai.data.PluginDataHandler;
import com.googlesource.gerrit.plugins.reviewai.data.PluginDataHandlerProvider;
import com.googlesource.gerrit.plugins.reviewai.errors.exceptions.AiConnectionFailException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

@Slf4j
public class OpenAiConversation extends OpenAiApiBase {
  public static final String KEY_CONVERSATION_ID = "conversationId";

  private final ChangeSetData changeSetData;
  private final PluginDataHandler changeDataHandler;

  public OpenAiConversation(
      Configuration config,
      ChangeSetData changeSetData,
      PluginDataHandlerProvider pluginDataHandlerProvider) {
    super(config);
    this.changeSetData = changeSetData;
    changeDataHandler = pluginDataHandlerProvider.getChangeScope();
  }

  public String resolveConversationId() throws AiConnectionFailException {
    String conversationId = changeDataHandler.getValue(KEY_CONVERSATION_ID);
    if (conversationId == null
        || !changeSetData.getForcedReview() && !changeSetData.getForcedStagedReview()) {
      return createConversation();
    }
    log.info(
        "Existing OpenAI conversation found for the Change Set. Conversation ID: {}",
        conversationId);
    return conversationId;
  }

  private String createConversation() throws AiConnectionFailException {
    Request request =
        httpClient.createRequestFromJson(OpenAiUriResourceLocator.conversationsUri(), new Object());
    log.debug("OpenAI Create Conversation request: {}", request);

    OpenAiResponse conversationResponse = getOpenAiResponse(request, OpenAiResponse.class);
    String conversationId = conversationResponse.getId();
    if (conversationId != null) {
      changeDataHandler.setValue(KEY_CONVERSATION_ID, conversationId);
      log.info("Conversation created: {}", conversationResponse);
    } else {
      log.error("Failed to create conversation. Response: {}", conversationResponse);
    }
    return conversationId;
  }

  public void clear() {
    changeDataHandler.removeValue(KEY_CONVERSATION_ID);
  }
}
