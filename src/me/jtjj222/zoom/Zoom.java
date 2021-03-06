package me.jtjj222.zoom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Zoom extends JavaPlugin implements Listener {
	Material magicItem; //SPIDER_EYE
	boolean leftMouseButton; //left, true
	boolean useOtherButton; //true
	boolean cancelInteract; //true
	boolean zoomOutOnDamage; //true
	boolean zoomOutOnItemChange; //false
	String message; //&2[Zoom] &aYour zoom level is now %level%.
	String valueDisplay; //%value% (%index% / %total%)
	//Has all the user-defined zoom levels
	public List<Integer> zoomLevels = new ArrayList<Integer>(); //[3; 8; 10; 12]
	
	//Stores <playername, number of times they have clicked>
	//If they click, they zoom to the zoomLevels[click_count].
	public HashMap<String, Integer> playersZoomedIn = new HashMap<String, Integer>();
	
	public void onEnable() {
		//Save default configuration
		this.saveDefaultConfig();
		//Register events
		this.getServer().getPluginManager().registerEvents(this, this);
		//Get material from config
		String configMagicItem = this.getConfig().getString("MagicItem");
		
		Material m = Material.getMaterial(configMagicItem);
		if (m == null) {
			//ID support
			m = Material.getMaterial(Integer.parseInt(configMagicItem));
			if(m == null) {
				getLogger().log(Level.INFO, "Could not find item " + configMagicItem + ". Using SPIDER_EYE instead.");
				m = Material.SPIDER_EYE;
			}
		}
		magicItem = m;
		//Get mouse settings		
		String configLeftMouseButton = this.getConfig().getString("Mouse_Button");
		if (configLeftMouseButton.equalsIgnoreCase("left")) leftMouseButton = true;
		else if (configLeftMouseButton.equalsIgnoreCase("right")) leftMouseButton = false;
		else {
			getLogger().log(Level.INFO, "Could not find mouse button " + configLeftMouseButton + ". Using left instead.");
			leftMouseButton = true;
		}
		//Use other button?
		useOtherButton = getConfig().getBoolean("Use_Other_Button");
		//Interact options
		cancelInteract = getConfig().getBoolean("Cancel_Interact");
		//Damage options
		zoomOutOnDamage = getConfig().getBoolean("Zoom_Out_On_Damage");
		//Item change options
		zoomOutOnItemChange = getConfig().getBoolean("Zoom_Out_On_Item_Change");
		//Load zoom levels
		zoomLevels = new ArrayList<Integer>();
		
		for(Integer i : getConfig().getIntegerList("Zoom_Levels"))
			if(i >= 0 && i <= 12) zoomLevels.add(i);
		
		if(zoomLevels.isEmpty()) {
			getLogger().info("Could not find any zoom valid zoom levels in the configuration. Filling with default values [3; 8; 10; 12].");
			zoomLevels.add(3);
			zoomLevels.add(8);
			zoomLevels.add(10);
			zoomLevels.add(12);
		} else if(zoomLevels.size() == 1 && zoomLevels.get(0) == 0) {
			getLogger().info("Your zoom levels only contain the value 0. The zoom item will be disabled.");
			//No need to take extra action, the plugin already knows what to do.
		}
		
		//Message
		message = ChatColor.translateAlternateColorCodes('&', getConfig().getString("Message"));
		if(ChatColor.stripColor(message).trim() == "") message = ""; //In case there's nothing visible :3
		//Value display
		valueDisplay = ChatColor.translateAlternateColorCodes('&', getConfig().getString("Value_Display"));
		
		if(!(valueDisplay.toLowerCase().contains("%value%") || valueDisplay.toLowerCase().contains("%index%") || valueDisplay.toLowerCase().contains("%total%"))) {
			getLogger().info("Invalid value display string. Using default: '%value% (%index% / %total%)'");
			valueDisplay = "%value% (%index% / %total%)";
		}
		
		//Schedule checking tasks
		getServer().getScheduler().runTaskTimerAsynchronously(this, new CheckTask(this), 0, 12000);
		
		//Up and runnin'!
		getLogger().info("Version " + getDescription().getVersion() + " enabled!");
	}
	@Override
	public void onDisable() {
		getLogger().info("Stopping tasks...");
		getServer().getScheduler().cancelTasks(this);
		
		getLogger().info("Version " + getDescription().getVersion() + " disabled.");
	}
	@EventHandler (priority = EventPriority.MONITOR)
	public void onPlayerDamagedByEntity (EntityDamageEvent e) {
		if (!zoomOutOnDamage || playersZoomedIn.isEmpty()) return;
		if (e.getEntity() instanceof Player) {
			Player p = (Player) e.getEntity();
			if (playersZoomedIn.containsKey(p.getName()) && p.getGameMode() != GameMode.CREATIVE)
				setZoomLevel(p, 0);
		}
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent e) {
		setZoomLevel(e.getPlayer(), 0);
	}
	@EventHandler(priority = EventPriority.MONITOR)
	public void onItemHeldChange(PlayerItemHeldEvent e) {
		//Feature request: zoom out on item change
		if(zoomOutOnItemChange && getClicks(e.getPlayer()) != 0) setZoomLevel(e.getPlayer(), 0);
	}
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent e) {
		if(!e.getPlayer().hasPermission("zoom.use")) return;
		
		Action zoomInAirAction = leftMouseButton ? Action.LEFT_CLICK_AIR : Action.RIGHT_CLICK_AIR;
		Action zoomInBlockAction = leftMouseButton ? Action.LEFT_CLICK_BLOCK : Action.RIGHT_CLICK_BLOCK;
		Action zoomOutAirAction = leftMouseButton ? Action.RIGHT_CLICK_AIR : Action.LEFT_CLICK_AIR;
		Action zoomOutBlockAction = leftMouseButton ? Action.RIGHT_CLICK_BLOCK : Action.LEFT_CLICK_BLOCK;
		
		if (e.getMaterial() == magicItem) {
			//Replaced the big ifs we had, since the setZoomLevel already handled out of bounds cases to match the default behaviour :)
			if (e.getAction() == zoomInAirAction || e.getAction() == zoomInBlockAction) {
				//Zoom in!
				setZoomLevel(e.getPlayer(), getClicks(e.getPlayer()) + 1);
				if(cancelInteract) e.setCancelled(true);
			} else if(useOtherButton && (e.getAction() == zoomOutAirAction || e.getAction() == zoomOutBlockAction)) {
				//Zoom out!
				setZoomLevel(e.getPlayer(), getClicks(e.getPlayer()) - 1);
				if(cancelInteract) e.setCancelled(true);
			}
		}
	}
	Integer getClicks(Player p) {
		if(playersZoomedIn.containsKey(p.getName())) return playersZoomedIn.get(p.getName());
		return 0;
	}
	void setZoomLevel(Player p, Integer clicks) {
		setZoomLevel(p, clicks, true);
	}
	void setZoomLevel(Player p, Integer clicks, boolean b) {
		if(p == null || !p.hasPermission("zoom.use")) return;
		
		int initialZoom = getClicks(p);
		
		if(clicks < 0) clicks = zoomLevels.size();
		
		p.removePotionEffect(PotionEffectType.SLOW);
		if(clicks == 0 || clicks > zoomLevels.size()) {
			clicks = 0;
			if(playersZoomedIn.containsKey(p.getName())) playersZoomedIn.remove(p.getName());
		} else {
			if(zoomLevels.get(clicks - 1) != 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 24000, zoomLevels.get(clicks - 1)));
			playersZoomedIn.put(p.getName(), clicks);
		}
		
		int zoomLevel = (clicks == 0 ? 0 : zoomLevels.get(clicks - 1));
		String level = valueDisplay.replace("%value%", "" + zoomLevel).replace("%index%", "" + clicks).replace("%total%", "" + zoomLevels.size());
		if(b && initialZoom != zoomLevel && !message.equals("")) p.sendMessage(message.replace("%level%", level));
	}
}
