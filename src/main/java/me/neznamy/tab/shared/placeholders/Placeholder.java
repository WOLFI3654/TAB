package me.neznamy.tab.shared.placeholders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.TAB;

/**
 * Representation of any placeholder
 */
public abstract class Placeholder {

	//refresh interval of the placeholder
	private int refresh;
	
	//placeholder identifier including %
	protected String identifier;
	
	//premium replacements
	private Map<Object, String> replacements = new HashMap<Object, String>();
	
	//placeholders used in outputs of replacements
	private List<String> outputPlaceholders = new ArrayList<String>();
	
	/**
	 * Constructs new instance with given parameters and loads placeholder output replacements
	 * @param identifier - placeholder identifier
	 * @param refresh - refresh interval in millieconds
	 */
	public Placeholder(String identifier, int refresh) {
		if (refresh % 50 != 0) throw new IllegalArgumentException("Refresh interval must be divisible by 50");
		if (!identifier.startsWith("%") || !identifier.endsWith("%")) throw new IllegalArgumentException("Identifier must start and end with %");
		this.identifier = identifier;
		this.refresh = refresh;
		if (TAB.getInstance().getConfiguration().premiumconfig != null) {
			Map<Object, Object> original = TAB.getInstance().getConfiguration().premiumconfig.getConfigurationSection("placeholder-output-replacements." + identifier);
			for (Entry<Object, Object> entry : original.entrySet()) {
				replacements.put(entry.getKey().toString().replace('&', '\u00a7'), entry.getValue().toString());
				for (String id : TAB.getInstance().getPlaceholderManager().detectAll(entry.getValue()+"")) {
					if (!outputPlaceholders.contains(id)) outputPlaceholders.add(id);
				}
			}
		}
	}
	
	/**
	 * Returns placeholder's identifier
	 * @return placeholder's identifier
	 */
	public String getIdentifier() {
		return identifier;
	}
	
	/**
	 * Returns placeholder's refresh interval
	 * @return placeholder's refresh interval
	 */
	public int getRefresh() {
		return refresh;
	}
	
	/**
	 * Returns list of all nested strings containing placeholder that this placeholder may return
	 * @return list of all nested strings containing placeholder that this placeholder may return
	 */
	public String[] getNestedStrings(){
		return replacements.values().toArray(new String[0]);
	}
	
	/**
	 * Replaces this placeholder in given string and returns output
	 * @param s - string to replace this placeholder in
	 * @param p - player to set placeholder for
	 * @return string with this placeholder replaced
	 */
	public String set(String s, TabPlayer p) {
		try {
			String value = getLastValue(p);
			if (value == null) value = "";
			String newValue = setPlaceholders(findReplacement(replacements, TAB.getInstance().getPlaceholderManager().color(value)), p);
			if (newValue.contains("%value%")) {
				newValue = newValue.replace("%value%", value);
			}
			return s.replace(identifier, newValue);
		} catch (Throwable t) {
			return TAB.getInstance().getErrorManager().printError(s, "An error occurred when setting placeholder " + identifier + (p == null ? "" : " for " + p.getName()), t);
		}
	}
	
	/**
	 * Finds placeholder output replacement
	 * @param replacements - map of replacements from premiumconfig
	 * @param originalOutput - original output of the placeholder
	 * @return replaced placeholder output
	 */
	public static String findReplacement(Map<Object, String> replacements, String originalOutput) {
		if (replacements.isEmpty()) return originalOutput;
		if (replacements.containsKey(originalOutput)) {
			return replacements.get(originalOutput).toString();
		}
		for (Entry<Object, String> entry : replacements.entrySet()) {
			String key = entry.getKey().toString();
			if (key.contains("-")) {
				try {
					float low = Float.parseFloat(key.split("-")[0]);
					float high = Float.parseFloat(key.split("-")[1]);
					float actualValue = Float.parseFloat(originalOutput.replace(",", ""));
					if (low <= actualValue && actualValue <= high) return entry.getValue().toString();
				} catch (NumberFormatException e) {
					//nope
				}
			}
		}
		if (replacements.containsKey("else")) return replacements.get("else").toString();
		return originalOutput;
	}
	
	/**
	 * Applies all placeholders from outputs
	 * @param text - replaced placeholder
	 * @param p - player to replace for
	 * @return text with replaced placeholders in output
	 */
	private String setPlaceholders(String text, TabPlayer p) {
		String replaced = text;
		for (String s : outputPlaceholders) {
			if (s.equals("%value%")) continue;
			Placeholder pl = TAB.getInstance().getPlaceholderManager().getPlaceholder(s);
			if (pl != null && replaced.contains(pl.getIdentifier())) replaced = pl.set(replaced, p);
		}
		return replaced;
	}
	
	/**
	 * Returns last known value for defined player
	 * @param p - player to check value for
	 * @return last known value
	 */
	public abstract String getLastValue(TabPlayer p);
}