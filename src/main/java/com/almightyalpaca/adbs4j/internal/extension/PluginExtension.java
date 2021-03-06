package com.almightyalpaca.adbs4j.internal.extension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;

import com.almightyalpaca.adbs4j.exception.PluginException;
import com.almightyalpaca.adbs4j.exception.PluginLoadingException;
import com.almightyalpaca.adbs4j.exception.PluginUnloadingException;
import com.almightyalpaca.adbs4j.plugins.Plugin;
import com.almightyalpaca.adbs4j.plugins.PluginInfo;
import com.almightyalpaca.adbs4j.util.FileUtil;
import com.almightyalpaca.adbs4j.util.StringUtil;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class PluginExtension {

	final File						folder;
	final File						jar;

	final ExtensionClassLoader		loader;

	final Plugin					plugin;

	final ExtensionManager			extensionManager;
	final ExtensionBridge			bridge;
	final Class<? extends Plugin>	pluginClass;

	@SuppressWarnings("unchecked")
	public PluginExtension(final ExtensionManager extensionManager, final String name) throws PluginException, IOException, PluginLoadingException {
		this.extensionManager = extensionManager;
		this.folder = new File(this.extensionManager.getPluginDir(), name);

		final File updateFile = new File(this.extensionManager.getPluginDir(), name + ".zip");

		if (updateFile.exists()) {
			try {
				final ZipFile zipFile = new ZipFile(updateFile);
				if (this.folder.exists()) {
					try {
						FileUtils.deleteDirectory(this.folder);
					} catch (final IOException e) {
						throw new PluginException("Error while deleting " + this.folder.getName(), e);
					}
				}
				zipFile.extractAll(this.folder.getAbsolutePath());
			} catch (final ZipException e) {
				throw new PluginException("Error while extracting " + updateFile.getName(), e);
			}
			updateFile.delete();
		}

		if (!this.folder.exists()) {
			throw new PluginException("Plugin " + name + "not found!");
		}

		this.jar = new File(this.folder, "Plugin.jar");
		if (!this.jar.exists()) {
			throw new PluginException("Plugin.jar does not exist: " + this.folder.getName());
		}

		final File libs = new File(this.folder, "libs");
		libs.mkdirs();

		final JarFile jarFile = new JarFile(this.jar);
		final List<URL> urls = new ArrayList<>();
		for (final File f : FileUtil.listfiles(libs)) {
			urls.add(f.toURI().toURL());
		}

		this.loader = new ExtensionClassLoader(this.jar.toURI().toURL(), urls.toArray(new URL[urls.size()]));

		final List<Class<? extends Plugin>> list = new ArrayList<>();

		final Enumeration<JarEntry> enumeration = jarFile.entries();
		while (enumeration.hasMoreElements()) {
			final JarEntry jarEntry = enumeration.nextElement();
			if (jarEntry.isDirectory() || !jarEntry.getName().endsWith(".class")) {
				continue;
			}

			try {
				final Class<?> clazz = this.loader.loadClass(StringUtil.replaceLast(jarEntry.getName().replace("/", "."), ".class", ""), true);
				if (Plugin.class.isAssignableFrom(clazz)) {
					list.add((Class<? extends Plugin>) clazz);
				}
			} catch (final ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		jarFile.close();

		if (list.size() > 1) {
			throw new PluginException("Too many Plugin classes found for " + this.folder.getName() + ": " + StringUtil.toPrettyString(list));
		} else if (list.size() < 1) {
			throw new PluginException("No Plugin class found: " + this.folder.getName());
		} else {
			this.pluginClass = list.get(0);
			try {
				Constructor<? extends Plugin> constructor;
				constructor = this.pluginClass.getConstructor();
				constructor.setAccessible(true);
				this.plugin = constructor.newInstance();
			} catch (final Exception e) {
				throw new PluginException(e);
			}

			this.bridge = this.plugin.getBridge();
			this.bridge.initialize(this);

		}
	}

	public final ExtensionBridge getBridge() {
		return this.bridge;
	}

	public PluginInfo getPluginInfo() {
		return this.plugin.getPluginInfo();
	}

	public void load() throws PluginLoadingException {
		this.bridge.loadPlugin();
	}

	public void unload() {
		try {
			this.bridge.unloadPlugin();
		} catch (final PluginUnloadingException e1) {
			e1.printStackTrace();
		}
		try {
			this.loader.close();
		} catch (final IOException e) {
			e.printStackTrace();
		}

	}

}