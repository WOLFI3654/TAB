package me.neznamy.tab.shared.features;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.cpu.TabFeature;
import me.neznamy.tab.shared.cpu.UsageType;
import me.neznamy.tab.shared.features.types.event.QuitEventListener;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.EnumPlayerInfoAction;
import me.neznamy.tab.shared.packets.PacketPlayOutPlayerInfo.PlayerInfoData;

/**
 * A small class fixing bugs in other plugins
 */
public class GhostPlayerFix implements QuitEventListener {

	@Override
	public void onQuit(TabPlayer disconnectedPlayer) {
		TAB.getInstance().getCPUManager().runTaskLater(200, "removing players", getFeatureType(), UsageType.PLAYER_QUIT_EVENT, new Runnable() {

			@Override
			public void run() {
				for (TabPlayer all : TAB.getInstance().getPlayers()) {
					if (all == disconnectedPlayer) continue;
					all.sendCustomPacket(new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.REMOVE_PLAYER, new PlayerInfoData(disconnectedPlayer.getUniqueId())), getFeatureType());
				}
			}
		});
	}

	@Override
	public TabFeature getFeatureType() {
		return TabFeature.GHOST_PLAYER_FIX;
	}
}