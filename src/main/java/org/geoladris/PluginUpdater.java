package org.geoladris;

import org.geoladris.config.Config;

public class PluginUpdater implements Runnable {
  private PluginDirsAnalyzer analyzer;
  private Config config;

  public PluginUpdater(PluginDirsAnalyzer analyzer, Config config) {
    this.analyzer = analyzer;
    this.config = config;
  }

  @Override
  public void run() {
    this.analyzer.reload();
    this.config.setPlugins(this.analyzer.getPlugins());
  }
}
