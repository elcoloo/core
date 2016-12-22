package org.geoladris.servlet;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.geoladris.Environment;
import org.geoladris.PluginDescriptor;
import org.geoladris.config.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ClientContentServletTest {
  @Captor
  private ArgumentCaptor<Config> configCaptor;

  private Properties systemProperties;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    systemProperties = System.getProperties();
    Properties props = new Properties();
    props.putAll(systemProperties);
    System.setProperties(props);
  }

  @After
  public void teardown() {
    System.setProperties(systemProperties);
  }

  /**
   * Mock ServletContext and initialize {@link AppContextListener} with it. Capture {@link Config}
   * instance.
   * 
   * @param folder
   * @throws ServletException
   */
  public ClientContentServlet setupConfigurationFolder(final String folder)
      throws ServletException {
    AppContextListener listener = new AppContextListener();
    ServletContextEvent servletContextEvent = mock(ServletContextEvent.class);
    ServletContext servletContext = mock(ServletContext.class);
    String confDir = folder.substring(0, folder.lastIndexOf('/'));
    String contextPath = folder.substring(folder.lastIndexOf('/') + 1);
    System.setProperty(Environment.CONFIG_DIR, confDir);
    when(servletContext.getResourcePaths("/WEB-INF/lib")).thenReturn(new HashSet<String>());
    Answer<String> answer = new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        return folder + invocation.getArguments()[0];
      }
    };
    when(servletContext.getRealPath(startsWith("/WEB-INF/"))).then(answer);
    when(servletContext.getRealPath(startsWith("WEB-INF/"))).then(answer);
    when(servletContext.getContextPath()).thenReturn(contextPath);
    when(servletContextEvent.getServletContext()).thenReturn(servletContext);
    listener.contextInitialized(servletContextEvent);

    verify(servletContext).setAttribute(eq(AppContextListener.ATTR_CONFIG), configCaptor.capture());

    ClientContentServlet servlet = new ClientContentServlet();
    ServletConfig servletConfig = mock(ServletConfig.class);
    when(servletConfig.getServletContext()).thenReturn(servletContext);
    when(servletContext.getAttribute(AppContextListener.ATTR_CONFIG))
        .thenReturn(configCaptor.getValue());
    servlet.init(servletConfig);
    return servlet;
  }

  @Test
  public void scanNoJavaPlugins() throws ServletException, IOException {
    setupConfigurationFolder("src/test/resources/testNoJavaPlugins");
    requirePaths("/testNoJavaPlugins");
  }

  @Test
  public void scanJavaRootModules() throws ServletException, IOException {
    setupConfigurationFolder("src/test/resources/testJavaNonRootModules");
    requirePaths("/testJavaNonRootModules");
  }

  @Test
  public void scanJavaRootSubfolders() throws ServletException, IOException {
    setupConfigurationFolder("src/test/resources/testJavaRootSubfolders");
    requirePaths("/testJavaRootSubfolders");
  }

  /**
   * Get descriptors from config instance and query all modules, styles and libs to the
   * {@link ClientContentServlet}
   * 
   * @param classpathPrefix
   * @throws ServletException
   * @throws IOException
   */
  private void requirePaths(String classpathPrefix) throws ServletException, IOException {
    Config config = configCaptor.getValue();

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession()).thenReturn(mock(HttpSession.class));

    List<String> modules = new ArrayList<>();
    List<String> styles = new ArrayList<>();
    List<String> nonRequirePaths = new ArrayList<>();
    PluginDescriptor[] enabled = config.getPluginConfig(Locale.ROOT, request);

    for (PluginDescriptor plugin : enabled) {
      modules.addAll(plugin.getModules());
      styles.addAll(plugin.getStylesheets());
      nonRequirePaths.addAll(plugin.getRequireJSPathsMap().values());
    }

    for (int i = 0; i < modules.size(); i++) {
      modules.set(i, "/modules/" + modules.get(i) + ".js");
    }
    testRequest(config, modules, classpathPrefix);

    for (int i = 0; i < nonRequirePaths.size(); i++) {
      nonRequirePaths.set(i, nonRequirePaths.get(i).substring(2) + ".js");
    }
    testRequest(config, nonRequirePaths, classpathPrefix);

    for (int i = 0; i < styles.size(); i++) {
      styles.set(i, "/" + styles.get(i));
    }
    testRequest(config, styles, classpathPrefix);
  }

  private void testRequest(Config config, Collection<String> collection, String classpathPrefix)
      throws ServletException, IOException {

    ClientContentServlet servlet = new ClientContentServlet();
    servlet.setTestingClasspathRoot(classpathPrefix + "/WEB-INF/classes/");
    ServletConfig servletConfig = mock(ServletConfig.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getAttribute("config")).thenReturn(config);
    when(servletConfig.getServletContext()).thenReturn(servletContext);
    servlet.init(servletConfig);

    assertTrue(collection.size() > 0);
    for (String path : collection) {
      int secondSlashIndex = path.indexOf("/", 1);
      String servletPath = path.substring(0, secondSlashIndex);
      String pathInfo = path.substring(secondSlashIndex);
      HttpServletResponse response = mock(HttpServletResponse.class);
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getServletPath()).thenReturn(servletPath);
      when(request.getPathInfo()).thenReturn(pathInfo);
      when(request.getSession()).thenReturn(mock(HttpSession.class));
      servlet.doGet(request, response);

      verify(response).setStatus(HttpServletResponse.SC_OK);
    }
  }

  @Test
  public void test404() throws ServletException, IOException {
    setupConfigurationFolder("src/test/resources/testNoJavaPlugins");

    ClientContentServlet servlet = new ClientContentServlet();
    servlet.setTestingClasspathRoot("/testNoJavaPlugins/WEB-INF/classes/");
    ServletConfig servletConfig = mock(ServletConfig.class);
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getAttribute("config")).thenReturn(configCaptor.getValue());
    when(servletConfig.getServletContext()).thenReturn(servletContext);
    servlet.init(servletConfig);

    check404(servlet, "/static/", "/parabailarlabamba");
    check404(servlet, "/modules/", "j/module-notexists");
    check404(servlet, "/modules/", "plugin1/module-notexists");
  }

  private void check404(ClientContentServlet servlet, String servletPath, String pathInfo)
      throws ServletException, IOException {
    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn(servletPath);
    when(request.getPathInfo()).thenReturn(pathInfo);
    when(request.getSession()).thenReturn(mock(HttpSession.class));

    try {
      servlet.doGet(request, response);
      fail();
    } catch (StatusServletException e) {
      assertEquals(404, e.getStatus());
    }
  }

  @Test
  public void test304NotModified() throws ServletException, IOException {
    ClientContentServlet servlet = setupConfigurationFolder("src/test/resources/testNoJavaPlugins");
    servlet.setTestingClasspathRoot("/testNoJavaPlugins/WEB-INF/classes/");


    HttpServletResponse response = mock(HttpServletResponse.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getServletPath()).thenReturn("/modules/plugin1/");
    when(request.getPathInfo()).thenReturn("a.js");
    when(request.getDateHeader("If-Modified-Since")).thenReturn(System.currentTimeMillis());
    when(request.getSession()).thenReturn(mock(HttpSession.class));

    servlet.doGet(request, response);

    verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
  }

  @Test
  public void test404ForDisabledPlugins() throws ServletException, IOException {
    ClientContentServlet servlet = setupConfigurationFolder("src/test/resources/testNoJavaPlugins");
    servlet.setTestingClasspathRoot("/testNoJavaPlugins/WEB-INF/classes/");

    Config config = spy(configCaptor.getValue());
    doReturn(new PluginDescriptor[0]).when(config).getPluginConfig(any(Locale.class),
        any(HttpServletRequest.class));
    when(servlet.getServletContext().getAttribute(AppContextListener.ATTR_CONFIG))
        .thenReturn(config);
    check404(servlet, "/modules/", "plugin1/a.js");
  }

  @Test
  public void svgContentType() throws Exception {
    ClientContentServlet servlet = setupConfigurationFolder("src/test/resources/testNoJavaPlugins");
    servlet.setTestingClasspathRoot("/testNoJavaPlugins/WEB-INF/classes/");

    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getSession()).thenReturn(mock(HttpSession.class));
    when(req.getServletPath()).thenReturn("/modules/");
    when(req.getPathInfo()).thenReturn("plugin1/images/image.svg");
    HttpServletResponse resp = mock(HttpServletResponse.class);

    servlet.doGet(req, resp);

    verify(resp).setContentType("image/svg+xml");
  }

  @Test
  public void doesNotReturnUnqualifiedResourcesAsQualified() throws Exception {
    ClientContentServlet servlet =
        setupConfigurationFolder("src/test/resources/testJavaNonRootModules");
    servlet.setTestingClasspathRoot("/testJavaNonRootModules/WEB-INF/classes/");
    check404(servlet, "/modules/", "testJavaRootSubfolders/module.js");
  }

  @Test
  public void doesNotReturnQualifiedResourcesAsUnqualified() throws Exception {
    ClientContentServlet servlet =
        setupConfigurationFolder("src/test/resources/testJavaNonRootModules");
    servlet.setTestingClasspathRoot("/testJavaNonRootModules/WEB-INF/classes/");
    check404(servlet, "/modules/", "module1.js");
  }
}
