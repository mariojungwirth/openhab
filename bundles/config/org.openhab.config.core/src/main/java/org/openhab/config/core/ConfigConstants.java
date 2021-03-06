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
package org.openhab.config.core;

/**
 * This class provides constants relevant for the configuration of openHAB
 * 
 * @author Kai Kreuzer
 * @since 0.3.0
 *
 */
public class ConfigConstants {

	/** The program argument name for setting the main config directory path */
	final static public String CONFIG_DIR_PROG_ARGUMENT = "openhab.configdir";
	
	/** The program argument name for setting the main config file name */
	final static public String CONFIG_FILE_PROG_ARGUMENT = "openhab.configfile";
	
	/** The main configuration directory name of openHAB */
	final static public String MAIN_CONFIG_FOLDER = "configurations"; 
	
	/** The default filename of the main openHAB configuration file */
	final static public String MAIN_CONFIG_FILENAME = "openhab.cfg";

	/** The default filename of the default openHAB configuration file */
	final static public String DEFAULT_CONFIG_FILENAME = "openhab_default.cfg";
}
