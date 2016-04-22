package com.goldornetwork.uhc.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;

import com.goldornetwork.uhc.UHC;
import com.goldornetwork.uhc.listeners.BackGround;
import com.goldornetwork.uhc.listeners.MoveEvent;
import com.goldornetwork.uhc.managers.GameModeManager.GameStartEvent;
import com.goldornetwork.uhc.managers.GameModeManager.State;
import com.goldornetwork.uhc.managers.world.ChunkGenerator;
import com.goldornetwork.uhc.managers.world.WorldFactory;
import com.goldornetwork.uhc.utils.MessageSender;
import com.google.common.collect.ImmutableSet;


public class ScatterManager implements Listener{

	//TODO check if spawn location is valid 

	//instances
	private UHC plugin;
	private TeamManager teamM;
	private MoveEvent moveE;
	private BackGround backG;
	private WorldFactory worldF;
	private ChunkGenerator chunkG;
	//storage
	private boolean scatterComplete;
	private int radius;
	private World uhcWorld;
	private final int LOADS_PER_SECOND = 1;
	private int timer;
	private BlockFace[] faces = new BlockFace[] { BlockFace.SELF, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST, BlockFace.NORTH_WEST};

	//storage
	private List<Location> validatedLocs = new ArrayList<Location>();
	private List<Chunk> chunksToKeepLoaded = new ArrayList<Chunk>();
	private Map<String, List<UUID>> teamToScatter = new HashMap<String, List<UUID>>();
	private Map<String, Location> locationsOfTeamSpawn = new HashMap<String, Location>();
	private Map<UUID, Location> locationsOfFFA = new HashMap<UUID, Location>();
	private List<UUID> lateScatters = new ArrayList<UUID>();
	private List<String> nameOfTeams = new ArrayList<String>();
	private List<UUID> FFAToScatter= new ArrayList<UUID>();

	public ScatterManager(UHC plugin, TeamManager teamM, MoveEvent moveE, BackGround backG, WorldFactory worldF, ChunkGenerator chunkG) {
		this.plugin=plugin;
		this.teamM=teamM;
		this.moveE=moveE;
		this.backG=backG;
		this.worldF=worldF;
		this.chunkG=chunkG;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	private void config(){
		plugin.getConfig().addDefault("radius", 1000);
		plugin.saveConfig();
	}

	/**
	 * Does the following: unfreezes players, disables pvp, disables mob spawning, disables natural regeneration, sets difficulty to hard, will clear scattered players, 
	 * will re-initialize the world border, will clear entities.
	 */
	public void setup(){
		config();
		newUHCWorld();
		moveE.unfreezePlayers();
		radius = plugin.getConfig().getInt("radius");
		getUHCWorld().setPVP(false);
		getUHCWorld().setGameRuleValue("doMobSpawning", "false");
		getUHCWorld().setGameRuleValue("naturalRegeneration", "false");
		getUHCWorld().setDifficulty(Difficulty.HARD);
		scatterComplete=false;
		teamToScatter.clear();
		locationsOfTeamSpawn.clear();
		lateScatters.clear();
		nameOfTeams.clear();
		WorldBorder wb = getUHCWorld().getWorldBorder();
		wb.setCenter(getUHCWorld().getSpawnLocation());
		wb.setSize(radius*2);
		wb.setDamageBuffer(0);
		wb.setDamageAmount(.5);
		wb.setWarningTime(15);
		wb.setWarningDistance(20);
		for(Entity e : getUHCWorld().getEntities()){
			if(!(e instanceof Player)){
				e.remove();
			}
		}
		chunkG.generate(getUHCWorld(), getCenter(), radius);

	}

	@EventHandler
	public void on(ChunkUnloadEvent e){
		if(State.getState().equals(State.SCATTER)){
			if(chunksToKeepLoaded.contains(e.getChunk())){
				e.setCancelled(true);
			}
		}
	}

	/**
	 * Used to check state of scattering
	 * @return <code> True </code> if scattering has completed
	 */
	public boolean isScatteringComplete(){
		return scatterComplete;
	}
	public void enableFFA(){
		FFAToScatter.addAll(teamM.getPlayersInGame());
		findLocations(FFAToScatter.size());
	}
	public void enableTeams(){
		for(String team : teamM.getActiveTeams()){
			nameOfTeams.add(team);
			teamToScatter.put(team, teamM.getPlayersOnATeam(team));
		}
		findLocations(nameOfTeams.size());
	}

	private void findLocations(int numberOfLocationsToFind){
		chunkG.cancelGeneration();
		timer=0;
		MessageSender.broadcast("Finding locations...");
		new BukkitRunnable() {

			@Override
			public void run() {
				int availMem = plugin.AvailableMemory();
				if(availMem<200){
					MessageSender.broadcast("low mem!");
					return;
				}

				else if(timer >=numberOfLocationsToFind){
					if(teamM.isFFAEnabled()){
						int j = 0;
						for(UUID u : teamM.getPlayersInGame()){
							locationsOfFFA.put(u, validatedLocs.get(j));
							j++;
						}
					}
					else if(teamM.isTeamsEnabled()){
						int j = 0;
						for(String team : teamM.getActiveTeams()){
							locationsOfTeamSpawn.put(team, validatedLocs.get(j));
							j++;
						}
					}
					generate(validatedLocs);
					cancel();
				}
				else{
					for(int i = 0; i<LOADS_PER_SECOND; i++){
						Location vLoc = findValidLocation(getUHCWorld(), radius);
						vLoc.add(0, 1, 0);
						validatedLocs.add(vLoc);
						++timer;
					}
				}


			}
		}.runTaskTimer(plugin, 0L, 20L);
	}
	private void generate(List<Location> loc){
		timer=0;
		MessageSender.broadcast("Generating...");
		new BukkitRunnable() {

			@Override
			public void run() {
				int availMem = plugin.AvailableMemory();
				if(availMem<200){
					MessageSender.broadcast("low mem!");
					return;
				}
				if(timer>=loc.size()){
					timer=0;
					if(teamM.isFFAEnabled()){
						scatterFFA();
					}
					else if(teamM.isTeamsEnabled()){
						scatterTeams();
					}
					cancel();
				}
				else{
					getUHCWorld().loadChunk(loc.get(timer).getBlockX(), loc.get(timer).getBlockZ(), true);
					chunksToKeepLoaded.add(getUHCWorld().getChunkAt(loc.get(timer)));
					++timer;
				}

			}
		}.runTaskTimer(plugin, 0L, 20L);
	}
	/**
	 * Will scatter all teams and freeze them until scattering has completed
	 */
	private void scatterTeams(){
		timer=0;
		MessageSender.broadcast("Scattering teams...");
		moveE.freezePlayers();
		new BukkitRunnable() {
			@Override
			public void run() {
				int availMem = plugin.AvailableMemory();
				if(availMem<200){
					return;
				}
				if(teamToScatter.isEmpty()){
					Bukkit.getServer().getLogger().info("Error at scattering");
					cancel();
					return;
				}
				else{
					if(timer>=teamM.getActiveTeams().size()){
						setupStartingOptions();
						scatterComplete=true;
						cancel();
					}
					else{
						for(UUID u : teamToScatter.get(nameOfTeams.get(timer))){
							Location location = locationsOfTeamSpawn.get(nameOfTeams.get(timer)).clone();
							Location safeLocation = new Location(location.getWorld(), location.getBlockX(), location.getWorld().getHighestBlockYAt(location), location.getZ());
							OfflinePlayer p = Bukkit.getOfflinePlayer(u);
							if(p.isOnline()==false){
								lateScatters.add(u);
							}
							else if(p.isOnline()==true){
								Player target = (Player) p;
								initializePlayer(target);
								target.teleport(safeLocation);

							}
						}
						++timer;
					}

				}

			}
		}.runTaskTimer(plugin, 0L, 40L);
	}


	/**
	 * Will scatter all players in game and freeze them until complete
	 */
	private void scatterFFA(){
		timer=0;
		MessageSender.broadcast("Scattering players...");
		moveE.freezePlayers();
		new BukkitRunnable() {
			@Override
			public void run() {
				for(int i = 0; i<LOADS_PER_SECOND; i++){
					if(timer>=FFAToScatter.size()){
						scatterComplete=true;
						setupStartingOptions();
						cancel();
						return;
					}
					else{
						OfflinePlayer p = Bukkit.getOfflinePlayer(FFAToScatter.get(timer));
						Location location = locationsOfFFA.get(FFAToScatter.get(timer));
						Location safeLocation = new Location(location.getWorld(), location.getBlockX(), location.getWorld().getHighestBlockYAt(location), location.getZ());
						if(p.isOnline()==false){
							lateScatters.add(p.getUniqueId());
						}
						else if(p.isOnline()==true){
							Player target = (Player) p;
							initializePlayer(target);
							target.teleport(safeLocation);
						}
						++timer;
					}

				}

			}
		}.runTaskTimer(plugin, 0L, 20L);
	}

	/** Used to get the radius of the map
	 * @return <code> Integer </code> radius of the map
	 */
	public int getRadius(){
		return radius;
	}
	/**
	 * Used to get a safe teleportable location 
	 * @param world - the world the location should be found in
	 * @param radius - the radius of the world that the locations should be found within
	 * @return <code> Location </code> location of safe spawn
	 * @see validate()
	 */
	private Location findValidLocation(World world, int radius){
		boolean valid = false;
		Location location = null;
		while(valid ==false){
			Random random = new Random();
			int x = random.nextInt(radius * 2) - radius;
			int z = random.nextInt(radius * 2) - radius;
			x= x+ getCenter().getBlockX();
			z= z+ getCenter().getBlockZ();
			world.loadChunk(x, z, true);
			location = new Location(world, x, world.getHighestBlockYAt(x, z), z);
			if(validate(location.clone())){
				valid=true;
				break;
			}
			else{
				world.unloadChunkRequest(x, z);
			}
		}
		return location;
	}

	/**
	 * Used to check if the a given location is appropriate for teleporting conditions
	 * @param loc - the location to check
	 * @return <code> True </code> if location is safe to teleport player to
	 * @see findValidLocation()
	 */
	private boolean validate(Location loc){
		boolean valid = true;
		if(loc.getBlockY()<60){
			valid =false;
		}
		//value performance more than safety
		else if(loc.clone().add(0, -1, 0).getBlock().getType().isSolid()==false){
			valid = false;
		}
		else if(loc.clone().add(0, 0, 0).getBlock().getType().isSolid()){
			valid = false;
		}
		else if(loc.clone().add(0, 1, 0).getBlock().getType().isSolid()){
			valid = false;
		}
		else if(INVALID_SPAWN_BLOCKS.contains(loc.clone().add(0, -1, 0).getBlock().getType())){
			valid =false;
		}

		/*else{
			for(BlockFace face : faces){
				//getting the block at land
				if(INVALID_SPAWN_BLOCKS.contains(loc.clone().add(0, -1, 0).getBlock().getRelative(face).getType())){
					MessageSender.broadcast("Check 1: " + loc.getBlock().getRelative(face).getType().toString());
					valid=false;
					break;
				}

				//getting the block above land
				else if(loc.clone().add(0, 0, 0).getBlock().getRelative(face).getType().isSolid()){
					MessageSender.broadcast("Check 2: solid @ " + face.toString());
					valid=false;
					break;
				}

				//getting the block 2 above land
				else if(loc.clone().add(0, 1, 0).getBlock().getRelative(face).getType().isSolid()){
					MessageSender.broadcast("Check 3: solid @ " + face.toString());
					valid = false;
					break;
				}
			}

		}*/





		return valid;
	}


	public void handleLateScatter(Player p){
		if(teamM.isFFAEnabled()){
			lateScatterAPlayerInFFA(p);
			removePlayerFromLateScatters(p);
		}
		else if(teamM.isTeamsEnabled()){
			lateScatterAPlayerInATeam(teamM.getTeamOfPlayer(p), p);
			removePlayerFromLateScatters(p);
		}
	}
	/**
	 * Used to handle players who were disconnected and need to be scattered explicitly in a FFA context
	 * @param p - the player who needs to be teleported
	 */
	private void lateScatterAPlayerInFFA(Player p){
		Location location = locationsOfFFA.get(p.getUniqueId());
		Location safeLocation = new Location(location.getWorld(), location.getBlockX(), location.getWorld().getHighestBlockYAt(location), location.getZ());
		initializePlayer(p);
		p.teleport(safeLocation);
	}

	/**
	 * Used to handle players who were disconnected and need to be scattered explicitly in a team context
	 * @param team - the team of the player
	 * @param p - the player who needs to be teleported 
	 */
	private void lateScatterAPlayerInATeam(String team, Player p){
		initializePlayer(p);
		Location location = locationsOfTeamSpawn.get(team.toLowerCase());
		Location safeLocation = new Location(location.getWorld(), location.getBlockX(), location.getWorld().getHighestBlockYAt(location), location.getZ());
		p.teleport(safeLocation);
	}
	private void initializePlayer(Player p){
		p.getInventory().clear();
		p.getInventory().setArmorContents(null);
		p.setGameMode(GameMode.SURVIVAL);
		p.setBedSpawnLocation(getCenter());
		p.setLevel(0);
		p.setExp(0L);
	}
	/**
	 * Used to indicate that the player no longer needs to be scattered
	 * @param p - the player who no longer needs to be scattered
	 */
	public void removePlayerFromLateScatters(Player p){
		lateScatters.remove(p.getUniqueId());
	}


	/** Used to retrieve a list of players who need to be scattered
	 * @return <code> List </code> of players who need to be scattered
	 */
	public List<UUID> getLateScatters(){
		return this.lateScatters;
	}

	public void newUHCWorld(){
		uhcWorld = worldF.create();
	}
	/**
	 * Used to get the world the match is being played in
	 * @return <code> World </code> of match
	 */
	public World getUHCWorld(){
		return this.uhcWorld;
	}
	/**
	 * Used to retrieve the center of the match
	 * @return <code> Location </code> of the center of the match
	 */
	public Location getCenter(){
		return uhcWorld.getSpawnLocation();
	}

	/**
	 * Used to shrink the border of the world, ideally when PVP is enabled
	 */
	public void shrinkBorder(){
		getUHCWorld().getWorldBorder().setSize(400, 15*60);
		MessageSender.broadcast("The worldborder will now slowly shrink to a radius of 400.");
	}

	/**
	 * A list of blocks that we do not want players to spawn on
	 * @see findValidLocation()
	 * @see validate()
	 */
	private static final Set<Material> INVALID_SPAWN_BLOCKS = ImmutableSet.of(
			Material.STATIONARY_LAVA,
			Material.LAVA, 
			Material.WATER, 
			Material.STATIONARY_WATER, 
			Material.CACTUS,
			Material.LEAVES,
			Material.LEAVES_2,
			Material.AIR
			);

	/**
	 * Called when scattering has completed, it sets up world conditions
	 */
	private void setupStartingOptions() {
		//adding a slight delay 
		new BukkitRunnable() {

			@Override
			public void run() {
				Bukkit.getPluginManager().callEvent(new GameStartEvent());
				State.setState(State.INGAME);
				moveE.unfreezePlayers();
				MessageSender.broadcast("Scattering complete!");
				backG.unMutePlayers();
				getUHCWorld().setGameRuleValue("doMobSpawning", "true");
				getUHCWorld().setGameRuleValue("dodaylightcycle", "true");
				getUHCWorld().setTime(0);
			}
		}.runTaskLater(plugin, 100L);

	}

	public void prePVPSetup(){
		getUHCWorld().setTime(0);
		getUHCWorld().setGameRuleValue("dodaylightcycle", "false");
	}
	public World getLobby(){
		return worldF.getLobby();
	}

}
