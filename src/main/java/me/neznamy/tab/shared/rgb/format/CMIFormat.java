package me.neznamy.tab.shared.rgb.format;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatter for {#RRGGBB}
 */
public class CMIFormat extends RGBFormatter {

	//pattern for {#RRGGBB}
	private final Pattern pattern = Pattern.compile("\\{#[0-9a-fA-F]{6}\\}");
	
	@Override
	public String reformat(String text) {
		String replaced = text;
		Matcher m = pattern.matcher(replaced);
		while (m.find()) {
			String hexcode = m.group();
			String fixed = hexcode.substring(2, 8);
			replaced = replaced.replace(hexcode, "#" + fixed);
		}
		return replaced;
	}
}