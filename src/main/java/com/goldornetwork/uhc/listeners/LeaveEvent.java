package com.goldornetwork.uhc.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.goldornetwork.uhc.UHC;
import com.goldornetwork.uhc.managers.ScatterManager;
import com.goldornetwork.uhc.managers.TeamManager;

public class LeaveEvent implements Listener{

	//TODO if match has started and player leaves, spawn a chicken with their name, if afk for more than 5 mins then kill them and remove from team/game

	//instances
	private TeamManager teamM;
	private ScatterManager scatterM;
	//private ScatterManager scatterM = ScatterManager.getInstance();

	public LeaveEvent(UHC plugin, TeamManager teamM, ScatterManager scatterM) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		this.teamM=teamM;
		this.scatterM = scatterM;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerLeave(PlayerQuitEvent e){
		Player p = e.getPlayer();
		/*if(State.getState().equals(State.OPEN)){
			if(teamM.isPlayerInGame(p)){
				if(teamM.isFFAEnabled()){
					teamM.removePlayerFromFFA(p);
				}
				else if(teamM.isTeamsEnabled()){
					if(teamM.isPlayerOwner(p)){
						teamM.removePlayerFromOwner(p);
					}
					teamM.removePlayerFromTeam(p);
				}

			}
		}*/
		

	}

}
