/*
 * Copyright 2012-2014 eBay Software Foundation and selendroid committers.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.selendroid.server;

import io.selendroid.server.common.*;
import io.selendroid.server.common.exceptions.AppCrashedException;
import io.selendroid.server.common.exceptions.StaleElementReferenceException;
import io.selendroid.server.common.http.HttpRequest;
import io.selendroid.server.common.http.HttpResponse;
import io.selendroid.server.common.http.TrafficCounter;
import io.selendroid.server.extension.ExtensionLoader;
import io.selendroid.server.handler.*;
import io.selendroid.server.handler.alert.Alert;
import io.selendroid.server.handler.alert.AlertAccept;
import io.selendroid.server.handler.alert.AlertDismiss;
import io.selendroid.server.handler.alert.AlertSendKeys;
import io.selendroid.server.handler.extension.ExtensionCallHandler;
import io.selendroid.server.handler.network.GetNetworkConnectionType;
import io.selendroid.server.handler.script.ExecuteAsyncScript;
import io.selendroid.server.handler.script.ExecuteScript;
import io.selendroid.server.handler.timeouts.AsyncTimeoutHandler;
import io.selendroid.server.handler.timeouts.SetImplicitWaitTimeout;
import io.selendroid.server.handler.timeouts.TimeoutsHandler;
import io.selendroid.server.handler.uiautomatorv2handler.*;
import io.selendroid.server.handler.uiautomatorv2handler.ClickElement;
import io.selendroid.server.model.SelendroidDriver;
import io.selendroid.server.util.SelendroidLogger;

import java.net.URLDecoder;

public class AppiumServlet extends BaseServlet {
  private SelendroidDriver driver = null;
  protected ExtensionLoader extensionLoader = null;

  public AppiumServlet(SelendroidDriver driver, ExtensionLoader extensionLoader) {
    this.driver = driver;
    this.extensionLoader = extensionLoader;
    init();
  }
/* Prakash: register one test handler */
  protected void init() {
    System.out.println("Prakash: AppiumServlet initialized");
      register(postHandler, new NewSession("/wd/hub/session"));
      register(getHandler, new GetCapabilities("/wd/hub/session/:sessionId"));
      register(postHandler, new FindElement("/wd/hub/session/:sessionId/element"));
      register(postHandler, new SendKeys("/wd/hub/session/:sessionId/keys"));
    register(postHandler, new ClickElement("/wd/hub/session/:sessionId/element/:id/click"));

  }

  private void addHandlerAttributesToRequest(HttpRequest request, String mappedUri) {
    String sessionId = getParameter(mappedUri, request.uri(), ":sessionId");
    if (sessionId != null) {
      request.data().put(SESSION_ID_KEY, sessionId);
    }

    String command = getParameter(mappedUri, request.uri(), ":command");
    if (command != null) {
      request.data().put(COMMAND_NAME_KEY, command);
    }

    String id = getParameter(mappedUri, request.uri(), ":id");
    if (id != null) {
      request.data().put(ELEMENT_ID_KEY, URLDecoder.decode(id));
    }
    String name = getParameter(mappedUri, request.uri(), ":name");
    if (name != null) {
      request.data().put(NAME_ID_KEY, name);
    }

    request.data().put(DRIVER_KEY, driver);
  }

  @Override
  public void handleRequest(HttpRequest request, HttpResponse response, BaseRequestHandler handler) {
    if ("/favicon.ico".equals(request.uri()) && handler == null) {
      response.setStatus(404).end();
      return;
    } else if (handler == null) {
      response.setStatus(404).end();
      return;
    }
    Response result;
    try {
      addHandlerAttributesToRequest(request, handler.getMappedUri());
      if (!handler.commandAllowedWithAlertPresentInWebViewMode()) {
        SelendroidDriver driver =
            (SelendroidDriver) request.data().get(AppiumServlet.DRIVER_KEY);
        if (driver != null && driver.isAlertPresent()) {
          result =
              new SelendroidResponse(handler.getSessionId(request),
                  StatusCode.UNEXPECTED_ALERT_OPEN,
                  "Unhandled Alert present");
          handleResponse(request, response, (SelendroidResponse) result);
          return;
        }
      }
      result = handler.handle(request);
    } catch (StaleElementReferenceException se) {
      try {
        SelendroidLogger.error("StaleElementReferenceException", se);
        String sessionId = getParameter(handler.getMappedUri(), request.uri(), ":sessionId");
        result = new SelendroidResponse(sessionId, StatusCode.STALE_ELEMENT_REFERENCE, se);
      } catch (Exception e) {
        SelendroidLogger.error("Error responding to StaleElementReferenceException", e);
        replyWithServerError(response);
        return;
      }
    } catch (AppCrashedException ae) {
      try {
        SelendroidLogger.error("App crashed when handling request", ae);
        String sessionId = getParameter(handler.getMappedUri(), request.uri(), ":sessionId");
        result = new SelendroidResponse(sessionId, StatusCode.UNKNOWN_ERROR, ae);
      } catch (Exception e) {
        SelendroidLogger.error("Error responding to app crash", e);
        replyWithServerError(response);
        return;
      }
    } catch (Exception e) {
      SelendroidLogger.error("Error handling request.", e);
      replyWithServerError(response);
      return;
    }
    handleResponse(request, response, (SelendroidResponse) result);
    String trafficStatistics = String.format(
        "traffic_stats: rx_bytes %d tx_bytes %d",
        TrafficCounter.readBytes(),
        TrafficCounter.writtenBytes());
    SelendroidLogger.info(trafficStatistics);
  }
}
