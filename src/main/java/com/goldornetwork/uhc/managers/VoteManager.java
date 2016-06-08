package com.goldornetwork.uhc.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.goldornetwork.uhc.UHC;
import com.goldornetwork.uhc.managers.GameModeManager.GameModeManager;
import com.goldornetwork.uhc.managers.GameModeManager.Gamemode;
import com.goldornetwork.uhc.utils.MessageSender;

public class VoteManager implements Listener{

	private GameModeManager gamemodeM;
	private TeamManager teamM;
	private UHC plugin;
	private Random random = new Random();
	private final int NUMBEROFOPTIONS = 3;
	private final int AMOUNTTOENABLE = 3;
	private List<List<Gamemode>> options= new LinkedList<List<Gamemode>>();
	private Map<Integer, Integer> mostPopularVote = new HashMap<Integer, Integer>();
	private List<UUID> haveVoted = new ArrayList<UUID>();
	private boolean voteActive;

	public VoteManager(UHC plugin, GameModeManager gamemodeM, TeamManager teamM) {
		this.plugin=plugin;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		this.gamemodeM = gamemodeM;
		this.teamM=teamM;
	}

	public void setup(){
		mostPopularVote.clear();
		haveVoted.clear();
		options.clear();
		voteActive=false;
	}
	public int getNumberOfOptions(){
		return NUMBEROFOPTIONS;
	}
	public boolean isValidOption(int option){
		if(option> 0 && option <=NUMBEROFOPTIONS){
			return true;
		}
		return false;
	}

	public void broadcastOptions(){

		new BukkitRunnable() {

			@Override
			public void run() {

				MessageSender.broadcast(getMessage());				

			}
		}.runTaskLater(plugin, 100L);
	}

	private List<String> getMessage(){
		List<String> toBroadcast = new LinkedList<String>();
		toBroadcast.add("[Options]");
		
		for(int i = 0; i<getNumberOfOptions(); i++){
			//toBroadcast.add("Option " + (i + 1));	
			int comma = 0;

			StringBuilder str = new StringBuilder();

			for(Gamemode game : getOptions().get(i)){
				comma++;
				String message = ChatColor.AQUA + game.getProperName();
				String properMessage;
				if(comma<getOptions().get(i).size()){
					properMessage = message + ", ";
				}
				else{
					properMessage=message;
				}
				str.append(properMessage);

			}
			String msg = str.toString();
			toBroadcast.add("Option " + (i + 1) + ": " + msg);

		}
		toBroadcast.add(ChatColor.LIGHT_PURPLE + "Please use /vote [option], also /info [gamemode]");
		return toBroadcast;
	}

	public void generateOptions(){
		voteActive=true;
		for(int k = 0; k<NUMBEROFOPTIONS; k++){
			List<Gamemode> toAdd = new ArrayList<Gamemode>();
			toAdd.clear();
			for(int i =0; i<AMOUNTTOENABLE; i++){
				boolean matched = false;
				while(matched==false){
					Gamemode game;
					int index;
					index = random.nextInt(gamemodeM.getNumberOfGamemodes()) ;
					game = gamemodeM.getGameMode(gamemodeM.getGamemodes().get(index).getClass());
					while(isValid(game)==false){
						index = random.nextInt(gamemodeM.getNumberOfGamemodes());
						game = gamemodeM.getGameMode(gamemodeM.getGamemodes().get(index).getClass());
					}
					//TODO loop through options and check if selected gamemode is already an option
					if(toAdd.contains(game)){
						matched=false;
					}
					else{
						toAdd.add(game);
						matched = true;
						break;
					}
				}
			}
			options.add(toAdd);
			mostPopularVote.put(k, 0);
		}
	}
	private boolean isValid(Gamemode game){
		boolean valid = true;
		return valid;
	}


	public List<List<Gamemode>> getOptions(){
		return options;
	}

	public void enableOption(int choice){
		voteActive=false;
		List<String> toBroadcast = new LinkedList<String>();
		StringBuilder str = new StringBuilder();
		int comma=0;
		for(Gamemode game : options.get(choice)){
			game.enable(plugin);
			toBroadcast.add(ChatColor.AQUA + game.getName() + " has been enabled");
			comma++;
			String message = ChatColor.AQUA + game.getProperName();
			String properMessage;
			if(comma<getOptions().get(choice).size()){
				properMessage = message + ", ";
			}
			else{
				properMessage=message;
			}
			str.append(properMessage);

		}
		String msg = str.toString();
		MessageSender.broadcastSmallTitle(msg + " have been enabled.");
		MessageSender.broadcast(toBroadcast);
	}

	public boolean hasVoted(Player p){
		if(haveVoted.contains(p.getUniqueId())){
			return true;
		}
		else{
			return false;
		}
	}
	public void addVote(Player p, int choice){
		haveVoted.add(p.getUniqueId());
		mostPopularVote.replace(choice-1, (mostPopularVote.get(choice-1) + 1));
	}

	public int getWinner(){
		int maxVal = (Collections.max(mostPopularVote.values()));
		int currentWinner=0;
		for(Map.Entry<Integer, Integer> entry : mostPopularVote.entrySet()){
			if(entry.getValue()==maxVal){
				currentWinner=entry.getKey();
			}
		}
		return currentWinner;
	}

	public int getWinnerVotes(){
		return (Collections.max(mostPopularVote.values()));
	}

	public boolean isActive(){
		return voteActive;
	}

	@EventHandler
	public void on(PlayerJoinEvent e){
		Player target =e.getPlayer();
		if(isActive()){
			new BukkitRunnable() {

				@Override
				public void run() {
					if(target.isOnline()){
						MessageSender.send(getMessage(), target);
					}					
				}
			}.runTaskLater(plugin, 10L);

		}

	}


}
