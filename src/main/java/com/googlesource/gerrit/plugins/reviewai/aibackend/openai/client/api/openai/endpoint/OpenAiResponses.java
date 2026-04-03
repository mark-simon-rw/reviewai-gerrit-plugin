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

package com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.openai.endpoint;

import com.googlesource.gerrit.plugins.reviewai.aibackend.common.client.prompt.AiPromptFactory;
import com.googlesource.gerrit.plugins.reviewai.aibackend.common.model.data.ChangeSetData;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.OpenAiUriResourceLocator;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.openai.OpenAiApiBase;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.openai.OpenAiParameters;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.openai.OpenAiPoller;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.client.api.openai.OpenAiTools;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.model.api.openai.OpenAiAssistantTools;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.model.api.openai.OpenAiCreateResponseRequest;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.model.api.openai.OpenAiResponseInputItem;
import com.googlesource.gerrit.plugins.reviewai.aibackend.openai.model.api.openai.OpenAiResponsesResponse;
import com.googlesource.gerrit.plugins.reviewai.config.Configuration;
import com.googlesource.gerrit.plugins.reviewai.errors.exceptions.AiConnectionFailException;
import com.googlesource.gerrit.plugins.reviewai.interfaces.aibackend.common.client.code.context.ICodeContextPolicy;
import com.googlesource.gerrit.plugins.reviewai.interfaces.aibackend.openai.client.prompt.IAiPrompt;
import com.googlesource.gerrit.plugins.reviewai.aibackend.common.client.api.gerrit.GerritChange;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;

import java.util.ArrayList;
import java.util.List;

import static com.googlesource.gerrit.plugins.reviewai.utils.GsonUtils.getGson;

@Slf4j
public class OpenAiResponses extends OpenAiApiBase {
  @Getter private final String instructions;
  @Getter private final String model;
  @Getter private final Double temperature;
  private final ICodeContextPolicy codeContextPolicy;
  private final OpenAiPoller openAiPoller;

  private OpenAiCreateResponseRequest requestBody;

  public OpenAiResponses(
      Configuration config,
      ChangeSetData changeSetData,
      GerritChange change,
      ICodeContextPolicy codeContextPolicy) {
    super(config);
    IAiPrompt aiPromptOpenAi =
        AiPromptFactory.getAiPrompt(config, changeSetData, change, codeContextPolicy);
    OpenAiParameters openAiParameters = new OpenAiParameters(config, change.getIsCommentEvent());
    this.codeContextPolicy = codeContextPolicy;
    instructions = aiPromptOpenAi.getDefaultAiAssistantInstructions();
    model = config.getAiModel();
    temperature = openAiParameters.getAiTemperature();
    openAiPoller = new OpenAiPoller(config);
  }

  public OpenAiResponsesResponse createPromptResponse(String input, String conversationId)
      throws AiConnectionFailException {
    return createResponse(input, conversationId);
  }

  public OpenAiResponsesResponse createToolResponse(
      List<OpenAiResponseInputItem> input, String conversationId)
      throws AiConnectionFailException {
    return createResponse(input, conversationId);
  }

  public String getRequestBody() {
    return getGson().toJson(requestBody);
  }

  private OpenAiResponsesResponse createResponse(Object input, String conversationId)
      throws AiConnectionFailException {
    Request request = createRequest(input, conversationId);
    log.debug("OpenAI Create Response request: {}", request);

    OpenAiResponsesResponse response =
        getOpenAiResponse(request, OpenAiResponsesResponse.class);
    log.debug("OpenAI Response created: {}", response);

    return openAiPoller.runPoll(
        OpenAiUriResourceLocator.responseRetrieveUri(response.getId()),
        response,
        OpenAiResponsesResponse.class);
  }

  private Request createRequest(Object input, String conversationId) {
    String uri = OpenAiUriResourceLocator.responsesUri();
    log.debug("OpenAI Create Response request URI: {}", uri);
    OpenAiAssistantTools openAiAssistantTools = buildTools();

    requestBody =
        OpenAiCreateResponseRequest.builder()
            .model(model)
            .instructions(instructions)
            .input(input)
            .temperature(temperature)
            .tools(openAiAssistantTools.getTools())
            .conversation(conversationId)
            .build();
    log.debug("Request body for creating response: {}", requestBody);

    return httpClient.createRequestFromJson(uri, requestBody);
  }

  private OpenAiAssistantTools buildTools() {
    OpenAiTools openAiFormatRepliesTools = new OpenAiTools(OpenAiTools.Functions.formatReplies);
    OpenAiAssistantTools openAiAssistantTools =
        OpenAiAssistantTools.builder()
            .tools(new ArrayList<>(List.of(openAiFormatRepliesTools.retrieveFunctionTool())))
            .build();
    codeContextPolicy.updateOpenAiTools(openAiAssistantTools);
    return openAiAssistantTools;
  }
}
