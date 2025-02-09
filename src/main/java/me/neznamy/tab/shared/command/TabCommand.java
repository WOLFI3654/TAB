package me.neznamy.tab.shared.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.command.level1.AnnounceCommand;
import me.neznamy.tab.shared.command.level1.BossBarCommand;
import me.neznamy.tab.shared.command.level1.CpuCommand;
import me.neznamy.tab.shared.command.level1.CpuTestCommand;
import me.neznamy.tab.shared.command.level1.DebugCommand;
import me.neznamy.tab.shared.command.level1.GroupCommand;
import me.neznamy.tab.shared.command.level1.NTPreviewCommand;
import me.neznamy.tab.shared.command.level1.ParseCommand;
import me.neznamy.tab.shared.command.level1.PlayerCommand;
import me.neznamy.tab.shared.command.level1.PlayerUUIDCommand;
import me.neznamy.tab.shared.command.level1.ReloadCommand;
import me.neznamy.tab.shared.command.level1.ScoreboardCommand;
import me.neznamy.tab.shared.command.level1.SendCommand;
import me.neznamy.tab.shared.command.level1.SetCollisionCommand;
import me.neznamy.tab.shared.command.level1.WidthCommand;

/**
 * The core command handler
 */
public class TabCommand extends SubCommand {

	//tab instance
	private TAB tab;
	
	/**
	 * Constructs new instance with given parameter and registers all subcommands
	 * @param tab - tab instance
	 */
	public TabCommand(TAB tab) {
		super("tab", null);
		this.tab = tab;
		registerSubCommand(new AnnounceCommand());
		registerSubCommand(new BossBarCommand());
		registerSubCommand(new CpuCommand());
		registerSubCommand(new CpuTestCommand());
		registerSubCommand(new DebugCommand());
		registerSubCommand(new GroupCommand());
		registerSubCommand(new NTPreviewCommand());
		registerSubCommand(new ParseCommand());
		registerSubCommand(new PlayerCommand());
		registerSubCommand(new PlayerUUIDCommand());
		registerSubCommand(new ReloadCommand());
		registerSubCommand(new SendCommand());
		registerSubCommand(new SetCollisionCommand());
		if (tab.isPremium()) {
			registerSubCommand(new ScoreboardCommand());
			registerSubCommand(new WidthCommand());
		}
	}

	@Override
	public void execute(TabPlayer sender, String[] args) {
		if (args.length > 0) {
			String arg0 = args[0];
			SubCommand command = subcommands.get(arg0.toLowerCase());
			if (command != null) {
				if (command.hasPermission(sender)) {
					command.execute(sender, Arrays.copyOfRange(args, 1, args.length));
				} else {
					sendMessage(sender, getTranslation("no_permission"));
				}
			} else {
				help(sender);
			}
		} else {
			help(sender);
		}
	}
	
	/**
	 * Sends help menu to the sender
	 * @param sender - player who ran command or null if from console
	 */
	private void help(TabPlayer sender){
		if (sender == null) tab.getPlatform().sendConsoleMessage("&3TAB v" + tab.getPluginVersion(), true);
		if ((sender == null || sender.hasPermission("tab.admin"))) {
			String command = tab.getPlatform().getSeparatorType().equals("world") ? "/tab" : "/btab";
			sendMessage(sender, "&m                                                                                ");
			sendMessage(sender, " &8>> &3&l" + command + " reload");
			sendMessage(sender, "      - &7Reloads plugin and config");
			sendMessage(sender, " &8>> &3&l" + command + " &9group&3/&9player &3<name> &9<property> &3<value...>");
			sendMessage(sender, "      - &7Do &8/tab group/player &7to show properties");
			sendMessage(sender, " &8>> &3&l" + command + " ntpreview");
			sendMessage(sender, "      - &7Shows your nametag for yourself, for testing purposes");
			sendMessage(sender, " &8>> &3&l" + command + " announce bar &3<name> &9<seconds>");
			sendMessage(sender, "      - &7Temporarily displays bossbar to all players");
			sendMessage(sender, " &8>> &3&l" + command + " parse <placeholder> ");
			sendMessage(sender, "      - &7Test if a placeholder works");
			sendMessage(sender, " &8>> &3&l" + command + " debug [player]");
			sendMessage(sender, "      - &7displays debug information about player");
			sendMessage(sender, " &8>> &3&l" + command + " cpu");
			sendMessage(sender, "      - &7shows CPU usage of the plugin");
			sendMessage(sender, " &8>> &4&l" + command + " group/player <name> remove");
			sendMessage(sender, "      - &7Clears all data about player/group");
			sendMessage(sender, "&m                                                                                ");
		}
	}
	
	@Override
	public List<String> complete(TabPlayer sender, String[] arguments) {
		if (!sender.hasPermission("tab.tabcomplete")) return new ArrayList<String>();
		return super.complete(sender, arguments);
	}
}