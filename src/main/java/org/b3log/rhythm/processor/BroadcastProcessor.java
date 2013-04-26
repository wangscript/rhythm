/*
 * Copyright (c) 2009, 2010, 2011, 2012, B3log Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.b3log.rhythm.processor;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.b3log.latke.Keys;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.JSONRenderer;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Strings;
import org.b3log.rhythm.event.symphony.ArticleSender;
import org.b3log.rhythm.model.Article;
import org.b3log.rhythm.service.ArticleService;
import org.b3log.rhythm.service.BroadcastChanceService;
import org.json.JSONObject;

/**
 * Broadcast processor.
 * 
 * <ul>
 *   <li>Generates broadcast chances</li>
 *   <li>Add broadcast</li>
 * </ul>
 *
 * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
 * @version 1.0.0.1, Apr 24, 2013
 * @since 0.1.6
 */
@RequestProcessor
public final class BroadcastProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(BroadcastProcessor.class.getName());

    /**
     * Broadcast chance service.
     */
    private BroadcastChanceService broadcastChanceService = BroadcastChanceService.getInstance();

    /**
     * Article service.
     */
    private ArticleService articleService = ArticleService.getInstance();

    /**
     * Generates broadcast chances.
     * 
     * @param context the specified context
     * @throws Exception exception 
     */
    @RequestProcessing(value = "/broadcast/chance/gen", method = HTTPRequestMethod.GET)
    public void generateBroadcastChance(final HTTPRequestContext context) throws Exception {
        broadcastChanceService.generateBroadcastChances();
        broadcastChanceService.sendBroadcastChances();
    }

    /**
     * Adds a broadcast.
     * 
     * @param context the specified context
     * @param request the specified http servlet request, for example,
     * <pre>
     * {
     *     "b3logKey": "",
     *     "email": "",
     *     "clientRuntimeEnv": "",
     *     "clientTitle": "",
     *     "clientVersion": "",
     *     "clientName": "",
     *     "clientHost": "",
     *     "broadcast": {
     *         "title": "",
     *         "content": "",
     *         "link": "" // optional
     *     }
     * }
     * </pre>
     * @param response the specified http servlet response
     * @throws Exception exception 
     */
    @RequestProcessing(value = "/broadcast", method = HTTPRequestMethod.POST)
    public void addBroadcast(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final JSONRenderer renderer = new JSONRenderer();
        context.setRenderer(renderer);


        final JSONObject ret = new JSONObject().put(Keys.STATUS_CODE, false);
        renderer.setJSONObject(ret);

        final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, response);

        final String b3logKey = requestJSONObject.getString("b3logKey");
        final String email = requestJSONObject.getString("email");
        // TODO: verify b3 key

        if (!broadcastChanceService.hasBroadcastChance(email)) {
            LOGGER.log(Level.WARNING, "The user[email={0}] has no broadcast chance", email);

            return;
        }

        final JSONObject broadcast = requestJSONObject.getJSONObject("broadcast");

        final JSONObject addRequest = new JSONObject();
        final JSONObject article = new JSONObject();
        addRequest.put(Article.ARTICLE, article);

        /*
         * NOTE: The article.articlePermalink is always "aBroadcast", it is the difference between article and broadcast,
         * a broadcast dose not exist in client, so has no permalink
         * 
         * {
         *     "article": {
         *         "articleAuthorEmail": "DL88250@gmail.com",
         *         "articleContent": "&lt;p&gt;test&lt;\/p&gt;",
         *         "articleCreateDate": long,
         *         "articlePermalink": "aBroadcast"
         *         "articleTags": "B3log Broadcast",
         *         "articleTitle": "test",
         *         "clientArticleId": long,
         *         "oId": ""
         *     },
         *     "userB3Key": "",
         *     "clientName": "",
         *     "clientTitle": "",
         *     "clientVersion": "",
         *     "clientHost": "",
         *     "clientRuntimeEnv": "",
         *     "clientAdminEmail": ""
         * } 
         */
        final long time = System.currentTimeMillis();

        String content = broadcast.getString("content");
        final String link = broadcast.optString("link");
        if (!Strings.isEmptyOrNull(link)) {
            content += "<p><a href='" + link + ">" +link + "</a></p>";
        }

        article.put(Article.ARTICLE_AUTHOR_EMAIL, email);
        article.put(Article.ARTICLE_CONTENT, content);
        article.put("articleCreateDate", time);
        article.put(Article.ARTICLE_PERMALINK, "aBroadcast");
        article.put(Article.ARTICLE_TAGS_REF, "B3log Broadcast");
        article.put(Article.ARTICLE_TITLE, broadcast.getString("title"));
        article.put(Keys.OBJECT_ID, String.valueOf(time));

        ArticleProcessor.securityProcess(article);

        addRequest.put("userB3Key", b3logKey);
        addRequest.put("clientName", requestJSONObject.getString("clientName"));
        addRequest.put("clientTitle", requestJSONObject.getString("clientTitle"));
        addRequest.put("clientVersion", requestJSONObject.getString("clientVersion"));
        addRequest.put("clientHost", requestJSONObject.getString("clientHost"));
        addRequest.put("clientRuntimeEnv", requestJSONObject.getString("clientRuntimeEnv"));
        addRequest.put("clientName", requestJSONObject.getString("clientName"));
        addRequest.put("clientAdminEmail", email);

        ArticleSender.addArticleToSymphony(addRequest);
        
        broadcastChanceService.removeBroadcastChance(email);

        ret.put(Keys.STATUS_CODE, true);
    }
}