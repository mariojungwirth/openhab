/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2013, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */
package org.openhab.binding.nikobus.internal;

import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.nikobus.NikobusBindingProvider;
import org.openhab.binding.nikobus.internal.config.AbstractNikobusItemConfig;
import org.openhab.binding.nikobus.internal.core.NikobusAckMonitor;
import org.openhab.binding.nikobus.internal.core.NikobusCommand;
import org.openhab.binding.nikobus.internal.core.NikobusCommandListener;
import org.openhab.binding.nikobus.internal.core.NikobusCommandReceiver;
import org.openhab.binding.nikobus.internal.core.NikobusCommandSender;
import org.openhab.binding.nikobus.internal.core.NikobusInterface;
import org.openhab.binding.nikobus.internal.core.NikobusModule;
import org.openhab.binding.nikobus.internal.util.CommandCache;
import org.openhab.core.binding.AbstractBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nikobus event binding class. Receives all command events and forwards them to
 * the appropriate configured item.
 * 
 * @author Davy Vanherbergen
 * @since 1.3.0
 */
public class NikobusBinding extends AbstractBinding<NikobusBindingProvider> implements ManagedService {

	private static final Logger log = LoggerFactory.getLogger(NikobusBinding.class);

	private NikobusInterface serialInterface = new NikobusInterface();

	private NikobusCommandReceiver commandReceiver;

	private NikobusCommandSender commandSender;

	private CommandCache cache;

	private String cacheLocation;

	private long refreshInterval = 600;

	private int nextModuleToRefresh = 0;

	private ScheduledExecutorService refreshService;

	private ExecutorService statusRequestService = Executors.newSingleThreadExecutor();


	public NikobusBinding() {
		// setup a command receiver in it's own thread
		commandReceiver = new NikobusCommandReceiver(this);
		commandReceiver.setBufferQueue(serialInterface.getBufferQueue());
		Thread receiverThread = new Thread(commandReceiver);
		receiverThread.setName("Nikobus Receiver");
		receiverThread.setDaemon(true);
		receiverThread.start();
		log.debug("Started Command Receiver Thread.");

		commandSender = new NikobusCommandSender(serialInterface);
		Thread senderThread = new Thread(commandSender);
		senderThread.setName("Nikobus Sender");
		senderThread.setDaemon(true);
		senderThread.start();
		log.debug("Started Command Sender Thread.");
	}
	
	
	/**
	 * {@inheritDoc}
	 * 
	 * Redistribute an openHAB command to the nikobus binding for that item.
	 */
	@Override
	public void internalReceiveCommand(String itemName, Command command) {

		log.trace("Received command {} for item {}", command.toString(), itemName);

		// get the item's corresponding Nikobus binding configuration
		AbstractNikobusItemConfig itemBinding = null;
		for (NikobusBindingProvider provider : providers) {
			if (provider.providesBindingFor(itemName)) {
				itemBinding = provider.getItemConfig(itemName);
				break;
			}
		}

		if (providers.size() == 0) {
			log.error("No providers");
		}
		if (itemBinding == null) {
			log.trace("No item found");
			return;
		}

		try {
			// process the command
			itemBinding.processCommand(command, this);
		} catch (Exception e) {
			log.error(
				"Error processing commmand {} for item {} : {}",
				new Object[] { command.toString(), itemBinding.getName(), e.getMessage() });
		}

	}

	/**
	 * Send a status update to the openHab bus.
	 * 
	 * @param itemName
	 *            item for which to send update
	 * @param state
	 *            status
	 */
	public void postUpdate(String itemName, State state) {
		if (eventPublisher == null) {
			log.error("Could not send status update.  Event Publisher is missing");
			return;
		}

		log.trace("Sending status update to {} : {}", itemName, state);
		eventPublisher.postUpdate(itemName, state);
		return;

	}

	/**
	 * Get the active command cache.
	 * 
	 * @return CommandCache
	 */
	public CommandCache getCache() {

		if (cache == null) {
			cache = CommandCache.getCache(cacheLocation);
		}
		return cache;
	}

	/**
	 * Send a command via the serial port to the Nikobus.
	 * 
	 * @param nikobusCommand
	 *            command to send
	 * @throws Exception
	 *             TimeoutException if waitForAck is true and no ack was
	 *             received within the timeout limit.
	 */
	public void sendCommand(NikobusCommand cmd) throws Exception {

		if (cmd.getAck() != null) {
			// send & wait for ACK
			log.trace("Sending command with ack {}", cmd.getCommand());

			NikobusAckMonitor monitor = new NikobusAckMonitor(cmd);
			commandReceiver.register(monitor);
			try {
				monitor.waitForAck(commandSender);
			} finally {
				commandReceiver.unregister(monitor);
			}

		} else {
			// send only
			log.trace("Sending command without waiting for ack {}",
					cmd.getCommand());
			commandSender.sendCommand(cmd);
		}
	}

	/**
	 * Start a connection to nikobus.
	 */
	public void connect() {

		try {
			log.trace("Starting connection");
			serialInterface.connect();
		} catch (Exception e) {
			log.error(e.getMessage());
		}
	}


	/**
	 * @return nikobus connection status.
	 */
	public String getConnectionStatus() {

		if (serialInterface != null && serialInterface.isConnected()) {
			return "Connected to " + serialInterface.getPort();
		}
		return "Not Connected.";
	}


	/**
	 * @return NikobusBindingProvider
	 */
	private NikobusBindingProvider getBindingProvider() {		
		for (NikobusBindingProvider p : providers) {
			return p;
		}		
		return null;
	}
	
	
	/**
	 * Request a status update for the module with the given address. This will
	 * trigger a read command being sent to the nikobus if none is already
	 * pending. The read command will be send using a separate thread.
	 * 
	 * @param moduleAddress
	 *            address of the module for which to request the status update.
	 * @param delayedSend
	 *            when true, sending will wait for empty bus
	 */
	public void scheduleStatusUpdateRequest(String moduleAddress, boolean delayedSend) {

		NikobusModule module = getBindingProvider().getModule(moduleAddress);
		final NikobusCommand cmd = module.getStatusRequestCommand(this);
		cmd.setWaitForSilence(delayedSend);
		cmd.setMaxRetryCount(10);

		if (commandSender.isCommandRedundant(cmd)) {
			// no need to send, there is a similar command already pending
			return;
		}
		
		Runnable updater = new Runnable() {
			@Override
			public void run() {
				try {
					sendCommand(cmd);
				} catch (Exception e) {
					log.error("Error occurred during status request.", e);
				}
			}
		};

		statusRequestService.submit(updater);

	}

	/**
	 * Activate the binding provider. After initial connection;
	 */
	public void activate() {

		log.trace("Activating binding");
		startRefreshScheduler();
		
		log.trace("Registering modules");
		for (NikobusModule module : getBindingProvider().getAllModules()) {
			register(module);
		}	
	}

	
	/**
	 * Start an automatic refresh scheduler.
	 */
	private void startRefreshScheduler() {

		// stop any running instance..
		if (refreshService != null) {
			refreshService.shutdownNow();
		}

		final NikobusBinding self = this;
		
		if (refreshInterval > 0) {
			refreshService = Executors.newScheduledThreadPool(1);

			Runnable refreshCommand = new Runnable() {

				@Override
				public void run() {

					List<NikobusModule> allModules = getBindingProvider().getAllModules();
					
					if (allModules == null || allModules.isEmpty()) {
						log.trace("No modules available to refresh");
						return;
					}

					if (nextModuleToRefresh >= allModules.size()) {
						nextModuleToRefresh = 0;
					}

					NikobusModule m = allModules.get(nextModuleToRefresh);

					log.trace("Requesting scheduled status update for {}", m.getAddress());
					NikobusCommand cmd = m.getStatusRequestCommand(self);
					cmd.setWaitForSilence(true);
					try {
						sendCommand(cmd);
					} catch (Exception e) {
						log.error("Error occurred during scheduled status refresh.", e);
					}
					nextModuleToRefresh++;
				}

			};

			// don't start the scheduler too soon, otherwise the connection may
			// not be available yet on slower systems like a pi
			refreshService.scheduleAtFixedRate(refreshCommand, 600, refreshInterval, TimeUnit.SECONDS);
			log.debug(
				"Refresh service started. Will refresh a module every {} seconds.",
				refreshInterval);
		}

	}

	/**
	 * Cleanup when binding is unloaded by DS.
	 */
	public void deactivate() {

		log.trace("Deactivating binding");

		refreshService.shutdownNow();
		commandReceiver.stop();
		commandSender.stop();

		serialInterface.disconnect();

	}

	/**
	 * Register a new item binding.
	 * 
	 * @param itemBinding
	 */
	public void register(NikobusCommandListener itemBinding) {
		commandReceiver.register(itemBinding);
	}

	/**
	 * Unregister a new item binding.
	 * 
	 * @param itemBinding
	 */

	public void unregister(NikobusCommandListener itemBinding) {
		commandReceiver.unregister(itemBinding);
	}

	/**
	 * Update the configuration.
	 */
	public void updated(Dictionary<String, ?> config)
			throws ConfigurationException {
		if (config != null) {
			String configuredCacheLocation = (String) config.get("cache.location");
			if (StringUtils.isNotBlank(configuredCacheLocation)) {
				log.trace("Setting cache location to {}", configuredCacheLocation);
				cacheLocation = configuredCacheLocation;
			}

			String configuredSerialPort = (String) config.get("serial.port");
			if (StringUtils.isNotBlank(configuredSerialPort)) {
				log.trace("Setting serial port to {}", configuredSerialPort);
				serialInterface.setPort(configuredSerialPort);
			}

			String configuredInterval = (String) config.get("refresh");
			if (StringUtils.isNotBlank(configuredInterval)) {
				refreshInterval = Long.parseLong(configuredInterval);
				log.trace("Setting refresh interval to {}", refreshInterval);
				startRefreshScheduler();
			}
			connect();

		}
	}

	@Override
	public void allBindingsChanged(BindingProvider provider) {
		
		// clear all previous listeners..
		commandReceiver.unregisterAll();
		
		NikobusBindingProvider bindingProvider = (NikobusBindingProvider) provider;
		for (String itemName : provider.getItemNames()) {
			if (provider.providesBindingFor(itemName)) {
				register(bindingProvider.getItemConfig(itemName));
				log.trace("Registering command listener for item {} ", itemName);
			}
		}

	}

	@Override
	public void bindingChanged(BindingProvider provider, String itemName) {
		NikobusBindingProvider bindingProvider = (NikobusBindingProvider) provider;
		if (!provider.providesBindingFor(itemName)) {
			log.trace("Removing command listener for item {}", itemName);
			commandReceiver.unregisterItem(itemName);
		} else {
			log.trace("Registering command listener for item {} ", itemName);
			register(bindingProvider.getItemConfig(itemName));
		}
	}

}
