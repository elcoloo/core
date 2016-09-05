package org.fao.unredd.portal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.fao.unredd.AppContextListener;
import org.fao.unredd.jwebclientAnalyzer.PluginDescriptor;
import org.fao.unredd.jwebclientAnalyzer.PluginDescriptorFileReader;
import org.junit.Before;
import org.junit.Test;

public class ConfigServletTest {
	private ConfigServlet servlet;
	private HttpServletRequest request;
	private HttpServletResponse response;
	private ServletContext context;
	private Config config;
	private ByteArrayOutputStream stream;

	@Before
	public void setup() throws IOException {
		this.servlet = new ConfigServlet();
		this.request = mock(HttpServletRequest.class);
		this.response = mock(HttpServletResponse.class);
		this.config = mock(Config.class);
		this.context = mock(ServletContext.class);
		this.stream = new ByteArrayOutputStream();
		PrintWriter writer = new PrintWriter(this.stream);
		when(this.context.getAttribute(AppContextListener.ATTR_CONFIG))
				.thenReturn(this.config);
		when(this.response.getWriter()).thenReturn(writer);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCustomizationModule() throws ServletException, IOException {
		Config config = mock(Config.class);
		when(config.getMessages(any(Locale.class))).thenReturn(
				new PropertyResourceBundle(
						new ByteArrayInputStream(new byte[0])));
		Properties portalProperties = new Properties();
		portalProperties.put("languages", "{\"es\": \"Espa\u00f1ol\"}");
		portalProperties.put("languages.default", "es");
		portalProperties.put("client.modules", "");
		portalProperties.put("map.initialZoomLevel", "5");
		portalProperties.put("moreproperties", "should not appear");
		when(config.getProperties()).thenReturn(portalProperties);
		List<Map<String, String>> languages = new ArrayList<Map<String, String>>();
		HashMap<String, String> spanish = new HashMap<String, String>();
		spanish.put("code", "es");
		spanish.put("name", "Español");
		languages.add(spanish);
		when(config.getLanguages()).thenReturn(languages.toArray(new Map[0]));
		when(config.getPropertyAsArray(Config.PROPERTY_MAP_CENTER)).thenReturn(
				new String[] { "0", "0" });
		when(config.getPropertyAsArray(Config.PROPERTY_CLIENT_MODULES))
				.thenReturn(new String[0]);

		PluginDescriptors pluginDescriptors = new PluginDescriptors(
				Collections.<PluginDescriptor> emptySet());
		when(
				config.getPluginConfig(any(Locale.class),
						any(HttpServletRequest.class))).thenReturn(
				pluginDescriptors);

		HttpServletRequest req = mock(HttpServletRequest.class);
		when(req.getAttribute("locale")).thenReturn(new Locale("es"));
		HttpServletResponse resp = mock(HttpServletResponse.class);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintWriter writer = new PrintWriter(baos);
		when(resp.getWriter()).thenReturn(writer);

		ConfigServlet servlet = getInitializedServlet(config);
		servlet.doGet(req, resp);
		writer.close();

		String response = new String(baos.toByteArray());

		assertTrue(response.contains("languages"));
		assertTrue(response.contains("languageCode"));
		assertTrue(response.contains("title"));
		assertTrue(response.contains(Config.PROPERTY_MAP_CENTER));
		assertTrue(response.contains("map.initialZoomLevel"));
		assertTrue(response.contains("modules"));
		assertFalse(response.contains("moreproperties"));
	}

	private ConfigServlet getInitializedServlet(Config config)
			throws ServletException {
		ConfigServlet servlet = new ConfigServlet();
		ServletConfig servletConfig = mock(ServletConfig.class);
		ServletContext servletContext = mock(ServletContext.class);
		when(servletContext.getAttribute("config")).thenReturn(config);
		when(servletContext.getAttribute("js-paths")).thenReturn(
				new ArrayList<String>());
		when(servletContext.getAttribute("plugin-configuration")).thenReturn(
				new HashMap<String, JSONObject>());
		when(servletConfig.getServletContext()).thenReturn(servletContext);
		servlet.init(servletConfig);
		return servlet;
	}

	@Test
	public void usesModulesFromPluginsInConfiguration() throws Exception {
		PluginDescriptor plugin1 = new PluginDescriptor();
		plugin1.addModule("module1");
		new PluginDescriptorFileReader(
				"{default-conf:{module1 : {prop1 : 42, prop2 : true}}}", true,
				"plugin1").fillPluginDescriptor(plugin1);
		Set<PluginDescriptor> plugins = new HashSet<PluginDescriptor>();
		plugins.add(plugin1);
		PluginDescriptors pluginDescriptors = new PluginDescriptors(plugins);

		mockEmptyConfig();
		when(request.getAttribute(LangFilter.ATTR_LOCALE)).thenReturn(
				Locale.ROOT);
		when(config.getPluginConfig(Locale.ROOT, request)).thenReturn(
				pluginDescriptors);

		servlet.doGet(request, response, context);

		String content = response();
		// It starts with var require = {...
		JSONObject json = JSONObject.fromObject(response().substring(
				content.indexOf('{')));
		JSONArray modules = json.getJSONObject("config")
				.getJSONObject("customization").getJSONArray("modules");
		assertEquals(1, modules.size());
		assertEquals("module1", modules.get(0));
	}

	@Test
	public void writesRequireJSConfigurationAsReturnedByConfig()
			throws Exception {
		PluginDescriptor plugin1 = new PluginDescriptor();
		plugin1.getModules().add("module1");
		PluginDescriptor plugin2 = new PluginDescriptor();
		plugin2.getModules().add("module2");
		plugin2.getModules().add("module3");
		new PluginDescriptorFileReader(
				"{default-conf:{module1 : {prop1 : 42, prop2 : true}}}", true,
				"plugin1").fillPluginDescriptor(plugin1);
		new PluginDescriptorFileReader(
				"{default-conf:{module2 : {prop3 : 'test'},"
						+ "module3 : [4, 2, 9]}}", true, "plugin2")
				.fillPluginDescriptor(plugin2);
		Set<PluginDescriptor> plugins = new HashSet<PluginDescriptor>();
		plugins.add(plugin1);
		plugins.add(plugin2);
		PluginDescriptors pluginDescriptors = new PluginDescriptors(plugins);

		mockEmptyConfig();
		when(request.getAttribute(LangFilter.ATTR_LOCALE)).thenReturn(
				Locale.ROOT);
		when(config.getPluginConfig(Locale.ROOT, request)).thenReturn(
				pluginDescriptors);

		servlet.doGet(request, response, context);

		String content = response();
		// It starts with var require = {...
		JSONObject json = JSONObject.fromObject(response().substring(
				content.indexOf('{')));
		JSONObject cfg = json.getJSONObject("config");
		JSONObject module1 = cfg.getJSONObject("module1");
		JSONObject module2 = cfg.getJSONObject("module2");
		JSONArray module3 = cfg.getJSONArray("module3");

		assertEquals(42, module1.getInt("prop1"));
		assertTrue(module1.getBoolean("prop2"));
		assertEquals("test", module2.getString("prop3"));
		assertEquals(4, module3.get(0));
		assertEquals(2, module3.get(1));
		assertEquals(9, module3.get(2));
	}

	private void mockEmptyConfig() {
		ResourceBundle bundle = ResourceBundle.getBundle("messages");
		when(this.config.getMessages(any(Locale.class))).thenReturn(bundle);
		when(this.config.getProperties()).thenReturn(new Properties());
	}

	private String response() throws IOException {
		this.response.getWriter().flush();
		this.response.getWriter().close();
		this.stream.flush();
		this.stream.close();
		return this.stream.toString();
	}
}