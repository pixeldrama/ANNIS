/*
 * Copyright 2011 Corpuslinguistic working group Humboldt University Berlin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licsense is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package annis.gui;

import annis.gui.components.ExceptionDialog;
import annis.libgui.AnnisBaseUI;
import annis.libgui.InstanceConfig;
import annis.libgui.Helper;
import annis.gui.components.ScreenshotMaker;
import annis.gui.controlpanel.ControlPanel;
import annis.libgui.media.MediaController;
import annis.libgui.media.MimeTypeErrorListener;
import annis.libgui.media.MediaControllerImpl;
import annis.gui.model.PagedResultQuery;
import annis.gui.model.Query;
import annis.gui.querybuilder.QueryBuilderChooser;
import annis.gui.querybuilder.TigerQueryBuilderPlugin;
import annis.gui.flatquerybuilder.FlatQueryBuilderPlugin;
import annis.gui.servlets.ResourceServlet;
import annis.gui.tutorial.TutorialPanel;
import static annis.libgui.AnnisBaseUI.USER_LOGIN_ERROR;
import annis.libgui.AnnisUser;
import annis.libgui.media.PDFController;
import annis.libgui.media.PDFControllerImpl;
import annis.service.objects.AnnisCorpus;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;
import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.data.validator.EmailValidator;
import com.vaadin.event.ShortcutListener;
import com.vaadin.server.ErrorHandler;
import com.vaadin.server.ExternalResource;
import com.vaadin.server.Page;
import com.vaadin.server.Page.UriFragmentChangedEvent;
import com.vaadin.server.RequestHandler;
import com.vaadin.server.ThemeResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinResponse;
import com.vaadin.server.VaadinSession;
import com.vaadin.server.WebBrowser;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.themes.BaseTheme;
import com.vaadin.ui.themes.ChameleonTheme;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.xeoh.plugins.base.PluginManager;
import net.xeoh.plugins.base.util.uri.ClassURI;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.LoggerFactory;
import org.vaadin.cssinject.CSSInject;

/**
 * GUI for searching in corpora.
 *
 * @author Thomas Krause <thomas.krause@alumni.hu-berlin.de>
 */
@Push
@Theme("annis")
public class SearchUI extends AnnisBaseUI
  implements ScreenshotMaker.ScreenshotCallback,
  MimeTypeErrorListener,
  Page.UriFragmentChangedListener,
  LoginListener, ErrorHandler
{

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(
    SearchUI.class);

  // regular expression matching, CLEFT and CRIGHT are optional
  // indexes: AQL=1, CIDS=2, CLEFT=4, CRIGHT=6
  private Pattern citationPattern =
    Pattern.
    compile(
    "AQL\\((.*)\\),CIDS\\(([^)]*)\\)(,CLEFT\\(([^)]*)\\),)?(CRIGHT\\(([^)]*)\\))?",
    Pattern.MULTILINE | Pattern.DOTALL);

  private HorizontalLayout layoutToolbar;

  private Label lblUserName;

  private Button btLogin;

  private Button btLogout;

  private Button btBugReport;

  private ScreenshotMaker screenshot;

  private Throwable lastBugReportCause;

  private ControlPanel controlPanel;

  private TutorialPanel tutorial;

  private TabSheet mainTab;

  private Window windowLogin;

  private QueryBuilderChooser queryBuilder;

  private String bugEMailAddress;

  private QueryController queryController;

  private String lastQueriedFragment;

  private InstanceConfig instanceConfig;

  private CSSInject css;

  public final static int CONTROL_PANEL_WIDTH = 360;

  @Override
  protected void init(VaadinRequest request)
  {
    super.init(request);

    setErrorHandler(this);

    this.instanceConfig = getInstanceConfig(request);

    getPage().setTitle(
      instanceConfig.getInstanceDisplayName() + " (ANNIS Corpus Search)");

    JavaScript.getCurrent().addFunction("annis.gui.logincallback",
      new LoginCloseCallback());

    queryController = new QueryController(this);

    // always get the resize events directly
    setImmediate(true);

    VerticalLayout mainLayout = new VerticalLayout();
    setContent(mainLayout);

    mainLayout.setSizeFull();
    mainLayout.setMargin(false);

    screenshot = new ScreenshotMaker(this);
    addExtension(screenshot);

    css = new CSSInject(this);

    layoutToolbar = new HorizontalLayout();
    layoutToolbar.setWidth("100%");
    layoutToolbar.setHeight("-1px");

    mainLayout.addComponent(layoutToolbar);
    layoutToolbar.addStyleName("toolbar");
    layoutToolbar.addStyleName("border-layout");

    Button btAboutAnnis = new Button("About ANNIS");
    btAboutAnnis.addStyleName(ChameleonTheme.BUTTON_SMALL);
    btAboutAnnis.setIcon(new ThemeResource("info.gif"));

    btAboutAnnis.addClickListener(new AboutClickListener());

    btBugReport = new Button("Report Bug");
    btBugReport.addStyleName(ChameleonTheme.BUTTON_SMALL);
    btBugReport.setDisableOnClick(true);
    btBugReport.setIcon(new ThemeResource("../runo/icons/16/email.png"));
    btBugReport.addClickListener(new Button.ClickListener()
    {
      @Override
      public void buttonClick(ClickEvent event)
      {
        reportBug();
      }
    });

    String bugmail = (String) VaadinSession.getCurrent().getAttribute(
      "bug-e-mail");
    if (bugmail != null && !bugmail.isEmpty()
      && !bugmail.startsWith("${")
      && new EmailValidator("").isValid(bugmail))
    {
      this.bugEMailAddress = bugmail;
    }
    btBugReport.setVisible(canReportBugs());

    lblUserName = new Label("not logged in");
    lblUserName.setWidth("-1px");
    lblUserName.setHeight("-1px");
    lblUserName.addStyleName("right-aligned-text");

    btLogin = new Button("Login", new Button.ClickListener()
    {
      @Override
      public void buttonClick(ClickEvent event)
      {
        BrowserFrame frame = new BrowserFrame("login", new ExternalResource(
          Helper.getContext() + "/login"));
        frame.setWidth("100%");
        frame.setHeight("200px");

        windowLogin = new Window("ANNIS Login", frame);
        windowLogin.setModal(true);
        windowLogin.setWidth("400px");
        windowLogin.setHeight("250px");

        addWindow(windowLogin);
        windowLogin.center();
      }
    });
//    BrowserWindowOpener loginOpener =
//      new BrowserWindowOpener(Helper.getContext() + "/login");
//    loginOpener.setFeatures("height=200,width=300,resizable");
//    loginOpener.extend(btLogin);

    btLogout = new Button("Logout", new Button.ClickListener()
    {
      @Override
      public void buttonClick(ClickEvent event)
      {
        // logout
        Helper.setUser(null);
        Notification.show("Logged out", Notification.Type.TRAY_NOTIFICATION);
        updateUserInformation();
      }
    });

    btLogin.setSizeUndefined();
    btLogin.setStyleName(ChameleonTheme.BUTTON_SMALL);
    btLogin.setIcon(new ThemeResource("../runo/icons/16/user.png"));

    btLogout.setSizeUndefined();
    btLogout.setStyleName(ChameleonTheme.BUTTON_SMALL);
    btLogout.setIcon(new ThemeResource("../runo/icons/16/user.png"));

    Button btOpenSource = new Button("Help us to make ANNIS better!");
    btOpenSource.setStyleName(BaseTheme.BUTTON_LINK);
    btOpenSource.addListener(new Button.ClickListener()
    {
      @Override
      public void buttonClick(ClickEvent event)
      {
        Window w = new HelpUsWindow();
        w.setCaption("Help us to make ANNIS better!");
        w.setModal(true);
        w.setResizable(true);
        w.setWidth("600px");
        w.setHeight("500px");
        addWindow(w);
        w.center();
      }
    });


    layoutToolbar.addComponent(btAboutAnnis);
    layoutToolbar.addComponent(btBugReport);
    layoutToolbar.addComponent(btOpenSource);
    layoutToolbar.addComponent(lblUserName);
    layoutToolbar.addComponent(btLogin);

    layoutToolbar.setSpacing(true);
    layoutToolbar.setComponentAlignment(btAboutAnnis, Alignment.MIDDLE_LEFT);
    layoutToolbar.setComponentAlignment(btBugReport, Alignment.MIDDLE_LEFT);
    layoutToolbar.setComponentAlignment(btOpenSource, Alignment.MIDDLE_CENTER);
    layoutToolbar.setComponentAlignment(lblUserName, Alignment.MIDDLE_RIGHT);
    layoutToolbar.setComponentAlignment(btLogin, Alignment.MIDDLE_RIGHT);
    layoutToolbar.setExpandRatio(btOpenSource, 1.0f);

    //HorizontalLayout hLayout = new HorizontalLayout();
    final HorizontalSplitPanel hSplit = new HorizontalSplitPanel();
    hSplit.setSizeFull();

    mainLayout.addComponent(hSplit);
    mainLayout.setExpandRatio(hSplit, 1.0f);

    ExampleQueriesPanel autoGenQueries = new ExampleQueriesPanel(
      "example queries", this);

    controlPanel = new ControlPanel(queryController, instanceConfig,
      autoGenQueries);
    controlPanel.setWidth(100f, Layout.Unit.PERCENTAGE);
    controlPanel.setHeight(100f, Layout.Unit.PERCENTAGE);
    hSplit.setFirstComponent(controlPanel);

    tutorial = new TutorialPanel();
    tutorial.setHeight("99%");

    mainTab = new TabSheet();
    mainTab.setSizeFull();
    mainTab.addTab(autoGenQueries, "example queries");
    mainTab.addTab(tutorial, "Tutorial");

    queryBuilder = new QueryBuilderChooser(queryController, this, instanceConfig);
    mainTab.addTab(queryBuilder, "Query Builder");

    hSplit.setSecondComponent(mainTab);
    hSplit.setSplitPosition(CONTROL_PANEL_WIDTH, Unit.PIXELS);
    hSplit.addSplitterClickListener(
      new AbstractSplitPanel.SplitterClickListener()
    {
      @Override
      public void splitterClick(AbstractSplitPanel.SplitterClickEvent event)
      {
        if (event.isDoubleClick())
        {
          if (hSplit.getSplitPosition() == CONTROL_PANEL_WIDTH)
          {
            // make small
            hSplit.setSplitPosition(0.0f, Unit.PIXELS);
          }
          else
          {
            // reset to default width
            hSplit.setSplitPosition(CONTROL_PANEL_WIDTH, Unit.PIXELS);
          }
        }
      }
    });
//    hLayout.setExpandRatio(mainTab, 1.0f);

    addAction(new ShortcutListener("^Query builder")
    {
      @Override
      public void handleAction(Object sender, Object target)
      {
        mainTab.setSelectedTab(queryBuilder);
      }
    });

    addAction(new ShortcutListener("Tutor^eial")
    {
      @Override
      public void handleAction(Object sender, Object target)
      {
        mainTab.setSelectedTab(tutorial);
      }
    });

    getPage().addUriFragmentChangedListener(this);

    getSession().addRequestHandler(new CitationRequestHandler());
    getSession().addRequestHandler(new ResourceRequestHandler());

    getSession().setAttribute(MediaController.class, new MediaControllerImpl());

    getSession().setAttribute(PDFController.class, new PDFControllerImpl());

    loadInstanceFonts();

    checkCitation();
    lastQueriedFragment = "";
    evaluateFragment(getPage().getUriFragment());

    setPollInterval(10000);

    updateUserInformation();
  }

  @Override
  public void error(com.vaadin.server.ErrorEvent event)
  {
    log.error("Unknown error in some component: " + event.getThrowable().
      getLocalizedMessage(),
      event.getThrowable());
    ExceptionDialog.show(event.getThrowable());
  }

  public boolean canReportBugs()
  {
    return this.bugEMailAddress != null;
  }

  public void reportBug()
  {
    reportBug(null);
  }

  public void reportBug(Throwable cause)
  {
    lastBugReportCause = cause;
    screenshot.makeScreenshot();
    btBugReport.setCaption("bug report is initialized...");
  }

  private void loadInstanceFonts()
  {
    if (instanceConfig != null && css != null && instanceConfig.getFont() != null)
    {
      FontConfig cfg = instanceConfig.getFont();

      if (cfg.getSize() == null || cfg.getSize().isEmpty())
      {
        css.setStyles(
          "@import url(" + cfg.getUrl() + ");\n"
          + ".corpus-font-force {font-family: '" + cfg.getName() + "', monospace !important; }\n"
          + ".corpus-font {font-family: '" + cfg.getName() + "', monospace; }\n"
          // this one is for the virtual keyboard
          + "#keyboardInputMaster tbody tr td table tbody tr td {\n"
          + "  font-family: '" + cfg.getName() + "', 'Lucida Console','Arial Unicode MS',monospace; "
          + "}");
      }
      else
      {
        css.setStyles(
          "@import url(" + cfg.getUrl() + ");\n"
          + ".corpus-font-force {\n"
          + "  font-family: '" + cfg.getName() + "', monospace !important;\n"
          + "  font-size: " + cfg.getSize() + " !important;\n"
          + "}\n"
          + ".corpus-font {\n"
          + "  font-family: '" + cfg.getName() + "', monospace;\n"
          + "  font-size: " + cfg.getSize() + ";\n"
          + "}\n"
          + ".corpus-font .v-table-table {\n"
          + "    font-size: " + cfg.getSize() + ";\n"
          + "}"
          // this one is for the virtual keyboard
          + "#keyboardInputMaster tbody tr td table tbody tr td {\n"
          + "  font-family: '" + cfg.getName() + "', 'Lucida Console','Arial Unicode MS',monospace; "
          + "}");
      }
    }
    else
    {
      css.setStyles(
        // use original font definition from keyboard.css if no font given
        "#keyboardInputMaster tbody tr td table tbody tr td {\n"
        + "  font-family: 'Lucida Console','Arial Unicode MS',monospace;"
        + "}");
    }
  }

  private InstanceConfig getInstanceConfig(VaadinRequest request)
  {
    String instance = null;
    String pathInfo = request.getPathInfo();

    if (pathInfo != null && pathInfo.startsWith("/"))
    {
      pathInfo = pathInfo.substring(1);
    }
    if (pathInfo != null && pathInfo.endsWith("/"))
    {
      pathInfo = pathInfo.substring(0, pathInfo.length() - 1);
    }

    Map<String, InstanceConfig> allConfigs = loadInstanceConfig();

    if (pathInfo != null && !pathInfo.isEmpty())
    {
      instance = pathInfo;
    }

    if (instance != null && allConfigs.containsKey(instance))
    {
      // return the config that matches the parsed name
      return allConfigs.get(instance);
    }
    else if (allConfigs.containsKey("default"))
    {
      // return the default config
      return allConfigs.get("default");
    }
    else if (allConfigs.size() > 0)
    {
      // just return any existing config as a fallback
      log.
        warn(
        "Instance config {} not found or null and default config is not available.",
        instance);
      return allConfigs.values().iterator().next();
    }

    // default to an empty instance config
    return new InstanceConfig();
  }

  @Override
  protected void addCustomUIPlugins(PluginManager pluginManager)
  {
    pluginManager.addPluginsFrom(new ClassURI(TigerQueryBuilderPlugin.class).
      toURI());
    pluginManager.addPluginsFrom(new ClassURI(FlatQueryBuilderPlugin.class).
      toURI());
    pluginManager.addPluginsFrom(new ClassURI(ResourceServlet.class).toURI());
  }

  public void checkCitation()
  {
    Object origURLRaw = VaadinSession.getCurrent().getSession().getAttribute(
      "citation");
    if (origURLRaw == null || !(origURLRaw instanceof String))
    {
      return;
    }
    String origURL = (String) origURLRaw;
    String parameters = origURL.replaceAll(".*?/Cite(/)?", "");
    if (!"".equals(parameters) && !origURL.equals(parameters))
    {
      try
      {
        String decoded = URLDecoder.decode(parameters, "UTF-8");
        evaluateCitation(decoded);
      }
      catch (UnsupportedEncodingException ex)
      {
        log.error(null, ex);
      }
    }

  }

  public void evaluateCitation(String relativeUri)
  {
    Matcher m = citationPattern.matcher(relativeUri);
    if (m.matches())
    {
      // AQL
      String aql = "";
      if (m.group(1) != null)
      {
        aql = m.group(1);
      }

      // CIDS
      Set<String> selectedCorpora = new HashSet<String>();
      if (m.group(2) != null)
      {
        String[] cids = m.group(2).split(",");
        selectedCorpora.addAll(Arrays.asList(cids));
      }

      // filter by actually avaible user corpora in order not to get any exception later
      WebResource res = Helper.getAnnisWebResource();
      List<AnnisCorpus> userCorpora =
        res.path("query").path("corpora").
        get(new AnnisCorpusListType());

      LinkedList<String> userCorporaStrings = new LinkedList<String>();
      for (AnnisCorpus c : userCorpora)
      {
        userCorporaStrings.add(c.getName());
      }

      selectedCorpora.retainAll(userCorporaStrings);

      // CLEFT and CRIGHT
      if (m.group(4) != null && m.group(6) != null)
      {
        int cleft = 0;
        int cright = 0;
        try
        {
          cleft = Integer.parseInt(m.group(4));
          cright = Integer.parseInt(m.group(6));
        }
        catch (NumberFormatException ex)
        {
          log.error(
            "could not parse context value", ex);
        }
        queryController.
          setQuery(
          new PagedResultQuery(cleft, cright, 0, 10, null, aql, selectedCorpora));
      }
      else
      {
        queryController.setQuery(new Query(aql, selectedCorpora));
      }

      // remove all currently openend sub-windows
      Set<Window> all = new HashSet<Window>(getWindows());
      for (Window w : all)
      {
        removeWindow(w);
      }
    }
    else
    {
      showNotification("Invalid citation", Notification.Type.WARNING_MESSAGE);
    }

  }

  public void updateUserInformation()
  {
    if (layoutToolbar == null || lblUserName == null)
    {
      return;
    }
    if (isLoggedIn())
    {
      AnnisUser user = Helper.getUser();
      if (user != null)
      {
        lblUserName.setValue("logged in as \"" + ((AnnisUser) user).
          getUserName() + "\"");
        if (layoutToolbar.getComponentIndex(btLogin) > -1)
        {
          layoutToolbar.replaceComponent(btLogin, btLogout);
          layoutToolbar.setComponentAlignment(btLogout, Alignment.MIDDLE_RIGHT);
        }
      }
    }
    else
    {
      lblUserName.setValue("not logged in");
      if (layoutToolbar.getComponentIndex(btLogout) > -1)
      {
        layoutToolbar.replaceComponent(btLogout, btLogin);
        layoutToolbar.setComponentAlignment(btLogin, Alignment.MIDDLE_RIGHT);
      }
    }

    queryController.updateCorpusSetList();
  }

  @Override
  public void onLogin()
  {
    AnnisUser user = Helper.getUser();

    if (user == null)
    {
      Object loginErrorOject = VaadinSession.getCurrent().getSession().
        getAttribute(USER_LOGIN_ERROR);
      if (loginErrorOject != null && loginErrorOject instanceof String)
      {
        Notification.show((String) loginErrorOject,
          Notification.Type.WARNING_MESSAGE);
      }
      VaadinSession.getCurrent().getSession().removeAttribute(
        AnnisBaseUI.USER_LOGIN_ERROR);
    }
    else if (user.getUserName() != null)
    {
      Notification.show("Logged in as \"" + user.getUserName() + "\"",
        Notification.Type.TRAY_NOTIFICATION);
    }

    updateUserInformation();
  }

  public boolean isLoggedIn()
  {
    return Helper.getUser() != null;
  }

  public ControlPanel getControlPanel()
  {
    return controlPanel;
  }

  public InstanceConfig getInstanceConfig()
  {
    return instanceConfig;
  }

  @Override
  public void screenshotReceived(byte[] imageData, String mimeType)
  {
    btBugReport.setEnabled(true);
    btBugReport.setCaption("Report Bug");

    if (bugEMailAddress != null)
    {
      ReportBugWindow reportBugWindow =
        new ReportBugWindow(bugEMailAddress, imageData, mimeType,
        lastBugReportCause);

      reportBugWindow.setModal(true);
      reportBugWindow.setResizable(true);
      addWindow(reportBugWindow);
      reportBugWindow.center();
      lastBugReportCause = null;
    }
  }

  public QueryController getQueryController()
  {
    return queryController;
  }

  public TabSheet getMainTab()
  {
    return mainTab;
  }

  @Override
  public void notifyCannotPlayMimeType(String mimeType)
  {
    if (mimeType == null)
    {
      return;
    }

    if (mimeType.startsWith("audio/ogg") || mimeType.startsWith("video/web"))
    {
      String browserList =
        "<ul>"
        + "<li>Mozilla Firefox: <a href=\"http://www.mozilla.org/firefox\" target=\"_blank\">http://www.mozilla.org/firefox</a></li>"
        + "<li>Google Chrome: <a href=\"http://www.google.com/chrome\" target=\"_blank\">http://www.google.com/chrome</a></li>"
        + "</ul>";

      WebBrowser browser = getPage().getWebBrowser();

      // IE9 users can install a plugin
      Set<String> supportedByIE9Plugin = new HashSet<String>();
      supportedByIE9Plugin.add("video/webm");
      supportedByIE9Plugin.add("audio/ogg");
      supportedByIE9Plugin.add("video/ogg");

      if (browser.isIE()
        && browser.getBrowserMajorVersion() >= 9 && supportedByIE9Plugin.
        contains(mimeType))
      {
        new Notification("Media file type unsupported by your browser",
          "Please install the WebM plugin for Internet Explorer 9 from "
          + "<a href=\"https://tools.google.com/dlpage/webmmf\">https://tools.google.com/dlpage/webmmf</a> "
          + " or use a browser from the following list "
          + "(these are known to work with WebM or OGG files)<br/>"
          + browserList,
          Notification.Type.WARNING_MESSAGE, true).show(Page.getCurrent());
      }
      else
      {
        new Notification("Media file type unsupported by your browser",
          "Please use a browser from the following list "
          + "(these are known to work with WebM or OGG files)<br/>"
          + browserList,
          Notification.Type.WARNING_MESSAGE, true).show(Page.getCurrent());
      }
    }
    else
    {
      Notification.show(
        "Media file type \"" + mimeType + "\" unsupported by your browser!",
        "Try to check your browsers documentation how to enable "
        + "support for the media type or inform the corpus creator about this problem.",
        Notification.Type.WARNING_MESSAGE);
    }

  }

  @Override
  public void notifyMightNotPlayMimeType(String mimeType)
  {
    /*
     if(!warnedAboutPossibleMediaFormatProblem)
     {
     Notification notify = new Notification("Media file type \"" + mimeType  + "\" might be unsupported by your browser!",
     "This means you might get errors playing this file.<br/><br /> "
     + "<em>If you have problems with this media file:</em><br /> Try to check your browsers "
     + "documentation how to enable "
     + "support for the media type or inform the corpus creator about this problem.",
     Notification.Type.TRAY_NOTIFICATION, true);
     notify.setDelayMsec(15000);
     showNotification(notify);
     warnedAboutPossibleMediaFormatProblem = true;
     }
     */
  }

  @Override
  public void uriFragmentChanged(UriFragmentChangedEvent event)
  {
    evaluateFragment(event.getUriFragment());
  }

  private void evaluateFragment(String fragment)
  {
    // do nothing if not changed
    if (fragment == null || fragment.isEmpty() || fragment.equals(
      lastQueriedFragment))
    {
      return;
    }

    Map<String, String> args = Helper.parseFragment(fragment);

    Set<String> corpora = new TreeSet<String>();

    if (args.containsKey("c"))
    {
      String[] corporaSplitted = args.get("c").split("\\s*,\\s*");
      corpora.addAll(Arrays.asList(corporaSplitted));
    }

    if (args.containsKey("c") && args.size() == 1)
    {
      // special case: we were called from outside and should only select
      // our corpus
      Set<String> mappedCorpora = new HashSet<String>();
      // iterate over given corpora and map names if necessary
      for (String c : corpora)
      {
        if (instanceConfig.getCorpusMappings() != null
          && instanceConfig.getCorpusMappings().containsKey(c))
        {
          mappedCorpora.add(instanceConfig.getCorpusMappings().get(c));
        }
        else
        {
          mappedCorpora.add(c);
        }
      }

      // get list of all corpora
      WebResource rootRes = Helper.getAnnisWebResource();
      List<AnnisCorpus> allCorpora = rootRes.path("query").path("corpora")
        .get(new GenericType<List<AnnisCorpus>>()
      {
      });
      Set<String> allCorpusNames = new HashSet<String>();
      for (AnnisCorpus c : allCorpora)
      {
        allCorpusNames.add(c.getName());
      }

      // remove all corpora selections that do not exist
      boolean someCorporaRemoved = mappedCorpora.retainAll(allCorpusNames);

      if (someCorporaRemoved)
      {
        // show a warning message that the corpus was not imported yet
        new Notification("Linked corpus does not exist",
          "The corpus you wanted to access unfortunally does not (yet) exist in ANNIS<br/>"
          + "A possible reason is that it has not been imported yet. Please ask the "
          + "responsible person of the site that contained the link to import the corpus.",
          Notification.Type.WARNING_MESSAGE, true).show(Page.getCurrent());
      }
      else
      {
        queryController.setQuery(new Query("tok", mappedCorpora));
      }
    }
    else if (args.get("cl") != null && args.get("cr") != null)
    {
      // full query with given context
      queryController.setQuery(new PagedResultQuery(
        Integer.parseInt(args.get("cl")),
        Integer.parseInt(args.get("cr")),
        Integer.parseInt(args.get("s")), Integer.parseInt(args.get("l")),
        args.get("seg"),
        args.get("q"), corpora));
      queryController.executeQuery(true, true);
    }
    else
    {
      // use default context
      queryController.setQuery(new Query(args.get("q"), corpora));
      queryController.executeQuery(true, true);
    }
  }

  /**
   * Updates the browser address bar with the current query paramaters and the
   * query itself.
   *
   * This is for convenient reloading the vaadin app and easy copying citation
   * links.
   *
   * @param q The query where the parameters are extracted from.
   */
  public void updateFragment(PagedResultQuery q)
  {
    List<String> args = Helper.citationFragment(q.getQuery(), q.getCorpora(),
      q.getContextLeft(), q.getContextRight(),
      q.getSegmentation(), q.getOffset(), q.getLimit());

    // set our fragment
    lastQueriedFragment = StringUtils.join(args, "&");
    UI.getCurrent().getPage().setUriFragment(lastQueriedFragment);

    // reset title
    getPage().setTitle(
      instanceConfig.getInstanceDisplayName() + " (ANNIS Corpus Search)");
  }

  /**
   * Adds the _c fragement to the URL in the browser adress bar when a corpus is
   * selected.
   *
   * @param corpora A list of corpora, which are add to the fragment.
   */
  public void updateFragementWithSelectedCorpus(Set<String> corpora)
  {
    if (corpora != null && !corpora.isEmpty())
    {
      String fragment = "_c=" + Helper.encodeBase64URL(StringUtils.
        join(corpora, ","));
      UI.getCurrent().getPage().setUriFragment(fragment);
    }
    else
    {
      UI.getCurrent().getPage().setUriFragment("");
    }
  }

  private class CitationRequestHandler implements RequestHandler
  {

    @Override
    public boolean handleRequest(VaadinSession session, VaadinRequest request,
      VaadinResponse response) throws IOException
    {
      checkCitation();
      return false;
    }
  }

  private class LoginCloseCallback implements JavaScriptFunction
  {

    @Override
    public void call(JSONArray arguments) throws JSONException
    {
      if (windowLogin != null)
      {
        removeWindow(windowLogin);

      }
      onLogin();
    }
  }

  private static class AboutClickListener implements ClickListener
  {

    public AboutClickListener()
    {
    }

    @Override
    public void buttonClick(ClickEvent event)
    {
      Window w = new AboutWindow();
      w.setCaption("About ANNIS");
      w.setModal(true);
      w.setResizable(true);
      w.setWidth("500px");
      w.setHeight("500px");
      UI.getCurrent().addWindow(w);
    }
  }

  private static class AnnisCorpusListType extends GenericType<List<AnnisCorpus>>
  {

    public AnnisCorpusListType()
    {
    }
  }

  public TabSheet getTabSheet()
  {
    return mainTab;
  }
}
