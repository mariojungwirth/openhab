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
package org.openhab.binding.digitalstrom.internal.config;

import org.openhab.binding.digitalstrom.internal.client.entity.DSID;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexander Betker
 * @author Alex Maier
 * @since 1.3.0
 */
public class DigitalSTROMBindingConfig implements BindingConfig{
	private static final Logger logger = LoggerFactory.getLogger(DigitalSTROMBindingConfig.class);

	public String itemName = null;
	public Item item=null;
	public DSID dsid = null;
	public DSID dsmid = null;
	public ConsumptionConfig consumption = ConsumptionConfig.ACTIVE_POWER;
	public int timeinterval = 0;
	public ContextConfig context = null;
	public short groupID = -1;
	public int zoneID = -1;
	public DigitalSTROMBindingConfig() {
		super();
	}
	
	public void init(Item item, String bindingConfig) throws BindingConfigParseException{
		this.itemName=item.getName();
		this.item=item;
		String[] elements = bindingConfig.trim().split("#");		// separator symbol
		for (int i=0; i<elements.length; i++) {			
			String[] pairs = elements[i].trim().split(":");			// symbol to separate a key from a value				
			if (pairs.length == 2) {								// to ensure to get pairs				
				DigitalSTROMBindingConfigEnum configKey = null;
				String keyStr= pairs[0].trim();
				String valueStr = pairs[1].trim();
				configKey = parseKey(keyStr);
				if(configKey==null)
					throw new BindingConfigParseException("ERROR in item: "+item.getName()+" configuration: key is NULL");
				if(valueStr.equals(""))
					throw new BindingConfigParseException("ERROR in item: "+item.getName()+" configuration: value is NULL");
				else
					parseValue(configKey, valueStr);
			}
			else {
				throw new BindingConfigParseException("ERROR in item: "+item.getName()+" configuration: you have used the symbol ':' more than one time ");
			}
		}
	}
	
	private DigitalSTROMBindingConfigEnum parseKey(String keyStr) throws BindingConfigParseException {
		DigitalSTROMBindingConfigEnum configKey=null;
		try {
			configKey = DigitalSTROMBindingConfigEnum.valueOf(keyStr);
		} catch (Exception e) {
			logger.error("UNKNOWN key in item configuration: "+keyStr);
			throw new BindingConfigParseException("ERROR in item: "+item.getName()+" UNKNOWN configuration: "+keyStr);
		}
		return configKey;
	}
	
	private void parseValue(DigitalSTROMBindingConfigEnum configKey, String valueStr) {
		switch (configKey) {
		
		case dsid:
			this.dsid = new DSID(valueStr);
			break;
		case consumption:
			try {
				this.consumption = ConsumptionConfig.valueOf(valueStr);
			}catch (Exception e) {
				logger.error("WRONG consumption type: "+valueStr+"; "+e.getLocalizedMessage());
			}
			if (consumption == null) {
				this.consumption = ConsumptionConfig.ACTIVE_POWER;
			}
			break;
		
		case timeinterval:
			int interval = -1;
			try {
				interval = Integer.parseInt(valueStr);
			} catch (java.lang.NumberFormatException e) {
				logger.error("Numberformat exception by parsing a string to int in timeinterval: "+valueStr);
			}
			if (interval != -1) {
				this.timeinterval = interval;
			}
			break;
		case dsmid:
			this.dsmid = new DSID(valueStr);
			break;
		case context:
			if (valueStr.toLowerCase().equals(ContextConfig.slat.name())) {
				this.context = ContextConfig.slat;
			}
			else if (valueStr.toLowerCase().equals(ContextConfig.apartment.name())) {
				this.context = ContextConfig.apartment;
			}
			else if (valueStr.toLowerCase().equals(ContextConfig.zone.name())) {
				this.context = ContextConfig.zone;
			}
			else if (valueStr.toLowerCase().equals(ContextConfig.awning.name())) {
				this.context = ContextConfig.awning;
			}
			break;
		case groupid:
			try {
				this.groupID = Short.parseShort(valueStr);
			} catch (java.lang.NumberFormatException e) {
				logger.error("Numberformat exception by parsing a string to short: "+valueStr+"; "+e.getLocalizedMessage());
			}
			break;
		case zoneid:
			try {
				this.zoneID = Integer.parseInt(valueStr);
			} catch (java.lang.NumberFormatException e) {
				logger.error("Numberformat exception by parsing a string to integer: "+valueStr+"; "+e.getLocalizedMessage());
			}
			break;
		default:
		}		
	}

	public boolean isValid() {
		if (dsid == null && dsmid == null) {
			if (!isValidSceneItem()) {
				return false;
			}
		}
		else if (dsmid != null) {
			if (!isValidMeterItem()) {
				return false;
			}
		}
		return true;
	}
	
	public boolean isValidMeterItem() {
		if (this.dsmid != null) {
			return (item instanceof NumberItem || item instanceof StringItem);
		}
		return false;
	}
	
	public boolean isValidSceneItem() {
		if (item instanceof NumberItem || item instanceof StringItem) {
			if (context == null) {
				return false;
			}
			else {
				if (context.equals(ContextConfig.apartment) || context.equals(ContextConfig.zone)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isValidDeviceMeterItem() {
		if (this.dsid != null) {
			return (item instanceof NumberItem || item instanceof StringItem);
		}
		return false;
	}
}
