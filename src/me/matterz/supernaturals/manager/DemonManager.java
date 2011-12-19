/*
 * Supernatural Players Plugin for Bukkit
 * Copyright (C) 2011  Matt Walker <mmw167@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package me.matterz.supernaturals.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import me.matterz.supernaturals.SuperNPlayer;
import me.matterz.supernaturals.SupernaturalsPlugin;
import me.matterz.supernaturals.io.SNConfigHandler;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;


public class DemonManager extends ClassManager{

	public DemonManager(){
		super();
	}

	private HashMap<Block, Location> webMap = new HashMap<Block, Location>();
	private ArrayList<Player> demonApps = new ArrayList<Player>();
	private List<Player> demons = new ArrayList<Player>();

	// -------------------------------------------- //
	// 					Damage Events				//
	// -------------------------------------------- //

	@Override
	public double victimEvent(EntityDamageEvent event, double damage){
		Player victim = (Player) event.getEntity();
		SuperNPlayer snVictim = SuperNManager.get(victim);

		if(event.getCause().equals(DamageCause.FIRE)
				|| event.getCause().equals(DamageCause.BLOCK_EXPLOSION)
				|| event.getCause().equals(DamageCause.ENTITY_EXPLOSION)
				|| event.getCause().equals(DamageCause.FIRE_TICK)){
			victim.setFireTicks(0);
			event.setCancelled(true);
			return 0;
		}else if(event.getCause().equals(DamageCause.FALL)){
			event.setCancelled(true);
			return 0;
		}else if(event.getCause().equals(DamageCause.LAVA)){
			final Player dPlayer = victim;
			if(!demons.contains(dPlayer)){
				demons.add(dPlayer);
				heal(victim);
				SuperNManager.alterPower(snVictim, SNConfigHandler.demonPowerGain, "Lava!");
				SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, new Runnable() {
					public void run() {
						demons.remove(dPlayer);
					}
				}, 41);
			}
			victim.setFireTicks(0);
			event.setCancelled(true);
			return 0;
		}
		return damage;
	}

	@Override
	public double damagerEvent(EntityDamageByEntityEvent event, double damage){
		Entity damager = event.getDamager();
		Player pDamager = (Player) damager;
		Entity victim = event.getEntity();
		Player pVictim = (Player) victim;
		SuperNPlayer snDamager = SuperNManager.get(pDamager);
		ItemStack item = pDamager.getItemInHand();

		if(SNConfigHandler.demonWeapons.contains(item.getType())){
			if(SNConfigHandler.debugMode)
				SupernaturalsPlugin.log(pDamager.getName() + " was not allowed to use "+item.getType().toString());
			SuperNManager.sendMessage(snDamager, "Demons cannot use this weapon!");
			damage=0;
		}
		if(victim instanceof Player) {
			double random = Math.random();
			if(random < 0.35) {
				pVictim.setFireTicks(SNConfigHandler.demonFireTicks);
			}
			return damage;
		}
		return damage;
	}

	@Override
	public void deathEvent(Player player){
		if(SNConfigHandler.debugMode)
			SupernaturalsPlugin.log("Player died.");

		SuperNPlayer snplayer = SuperNManager.get(player);
		EntityDamageEvent e = player.getLastDamageCause();

		SuperNManager.alterPower(snplayer, -SNConfigHandler.demonDeathPowerPenalty, "You died!");

		if(e==null)
			return;
		if(e.getCause().equals(DamageCause.DROWNING) && (player.getWorld().getTemperature(player.getLocation().getBlockX(), player.getLocation().getBlockZ())<0.6)){
			if(snplayer.isDemon()){
				if(SNConfigHandler.debugMode)
					SupernaturalsPlugin.log("Demon drowned.  Checking inventory...");
				if(player.getInventory().contains(Material.SNOW_BALL, SNConfigHandler.demonSnowballAmount)){
					SuperNManager.sendMessage(snplayer, "Your icy death has cooled the infernal fires raging within your body.");
					SuperNManager.cure(snplayer);
					if(SNConfigHandler.debugMode)
						SupernaturalsPlugin.log("Snowballs found!");
				}
			}
		}
	}

	@Override
	public void killEvent(SuperNPlayer damager, SuperNPlayer victim){
		if(victim==null){
			SuperNManager.alterPower(damager, SNConfigHandler.demonKillPowerCreatureGain, "Creature death!");
		}else{
			if(victim.getPower() > SNConfigHandler.demonKillPowerPlayerGain){
				SuperNManager.alterPower(damager, SNConfigHandler.demonKillPowerPlayerGain, "Player killed!");
			}else{
				SuperNManager.sendMessage(damager, "You cannot gain power from a player with no power themselves.");
			}
		}
	}

	// -------------------------------------------- //
	// 					Interact					//
	// -------------------------------------------- //

	@Override
	public boolean playerInteract(PlayerInteractEvent event){

		Action action = event.getAction();
		Player player = event.getPlayer();

		boolean cancelled = false;
		Material itemMaterial = event.getMaterial();

		if(action.equals(Action.LEFT_CLICK_AIR) || action.equals(Action.LEFT_CLICK_BLOCK)){
			if(player.getItemInHand()==null){
				return false;
			}

			if(itemMaterial.toString().equalsIgnoreCase(SNConfigHandler.demonMaterial)){
				if(SNConfigHandler.debugMode)
					SupernaturalsPlugin.log(player.getName()+" is casting FIREBALL with "+itemMaterial.toString());
				cancelled = fireball(player);
				if(!event.isCancelled() && cancelled)
					event.setCancelled(true);
				return true;
			}else if(itemMaterial.toString().equalsIgnoreCase(SNConfigHandler.demonSnareMaterial)){
				if(SNConfigHandler.debugMode)
					SupernaturalsPlugin.log(player.getName()+" is casting SNARE with "+itemMaterial.toString());
				Player target = SupernaturalsPlugin.instance.getSuperManager().getTarget(player);
				cancelled = snare(player, target);
				if(!event.isCancelled() && cancelled)
					event.setCancelled(true);
				return true;
			} else if(itemMaterial.toString().equalsIgnoreCase("NETHERRACK")) {
				Player victim = SupernaturalsPlugin.instance.getSuperManager().getTarget(player);
				if(victim == null) {
					return false;
				}
				convert(player, victim);
			}
		}
		return false;
	}

	// -------------------------------------------- //
	// 					Armor						//
	// -------------------------------------------- //

	@Override
	public void armorCheck(Player player){
		PlayerInventory inv = player.getInventory();
		ItemStack helmet = inv.getHelmet();
		ItemStack chest = inv.getChestplate();
		ItemStack leggings = inv.getLeggings();
		ItemStack boots = inv.getBoots();

		if(!(SNConfigHandler.demonArmor.contains(helmet.getType()))){
			inv.setHelmet(null);
			dropItem(player, helmet);
		}
		if(!(SNConfigHandler.demonArmor.contains(chest.getType()))){
			inv.setChestplate(null);
			dropItem(player, chest);
		}
		if(!(SNConfigHandler.demonArmor.contains(leggings.getType()))){
			inv.setLeggings(null);
			dropItem(player, leggings);
		}
		if(!(SNConfigHandler.demonArmor.contains(boots.getType()))){
			inv.setBoots(null);
			dropItem(player, boots);
		}
	}

	// -------------------------------------------- //
	// 				Demon Applications				//
	// -------------------------------------------- //

	public void checkInventory(Player player){
		PlayerInventory inv = player.getInventory();
		if(SNConfigHandler.debugMode)
			SupernaturalsPlugin.log("Player teleported to Nether.  Checking inventory...");
		ItemStack helmet = inv.getHelmet();
		ItemStack chestplate = inv.getChestplate();
		ItemStack leggings = inv.getLeggings();
		ItemStack boots = inv.getBoots();
		if(helmet.getType().equals(Material.LEATHER_HELMET)){
			if(SNConfigHandler.debugMode)
				SupernaturalsPlugin.log("Leather Helm");
			if(chestplate.getType().equals(Material.LEATHER_CHESTPLATE)){
				if(SNConfigHandler.debugMode)
					SupernaturalsPlugin.log("Leather Chest");
				if(leggings.getType().equals(Material.LEATHER_LEGGINGS)){
					if(SNConfigHandler.debugMode)
						SupernaturalsPlugin.log("Leather Legs");
					if(boots.getType().equals(Material.LEATHER_BOOTS)){
						if(SNConfigHandler.debugMode)
							SupernaturalsPlugin.log("Leather Boots");
						demonApps.add(player);
						return;
					}
				}
			}
		}
	}

	public boolean checkPlayerApp(Player player){
		if(demonApps.contains(player)){
			demonApps.remove(player);
			return true;
		}
		return false;
	}

	// -------------------------------------------- //
	// 					WebMap						//
	// -------------------------------------------- //

	public HashMap<Block, Location> getWebs(){
		return this.webMap;
	}

	public void removeWeb(Block block){
		webMap.remove(block);
	}

	public void removeAllWebs(){
		for(Block block : webMap.keySet()){
			block.setType(Material.AIR);
		}
	}

	// -------------------------------------------- //
	// 					Power Loss					//
	// -------------------------------------------- //

	public void powerAdvanceTime(Player player, int seconds){
		if(!player.getWorld().getEnvironment().equals(Environment.NETHER)){
			if(player.getLocation().getBlock().getType().equals(Material.FIRE) || player.getLocation().getBlock().getType().equals(Material.LAVA))
				return;
			SuperNPlayer snplayer = SuperNManager.get(player);
			SuperNManager.alterPower(snplayer, -(SNConfigHandler.demonPowerLoss*seconds));
		}
	}

	// -------------------------------------------- //
	// 						Healing					//
	// -------------------------------------------- //

	public void heal(Player player){
		if(player.isDead() || (player.getHealth() == 20))
			return;

		int health = player.getHealth();
		health += SNConfigHandler.demonHealing;
		if(health>20)
			health=20;
		player.setHealth(health);
	}

	// -------------------------------------------- //
	// 					Spells						//
	// -------------------------------------------- //

	public boolean fireball(Player player){
		SuperNPlayer snplayer = SuperNManager.get(player);
		if(!SupernaturalsPlugin.instance.getPvP(player)){
			SuperNManager.sendMessage(snplayer, "You cannot shoot fireballs in non-PvP areas");
			return false;
		}
		if(snplayer.getPower() < SNConfigHandler.demonPowerFireball){
			SuperNManager.sendMessage(snplayer, "Not enough power to cast fireball!");
			return false;
		}
		Location loc = player.getEyeLocation().toVector().add(player.getLocation().getDirection().multiply(2)).toLocation(player.getWorld(),
				player.getLocation().getYaw(), player.getLocation().getPitch());
		Fireball fireball = player.getWorld().spawn(loc, Fireball.class);
		fireball.setShooter(player);
		fireball.setYield(0);
		SuperNManager.alterPower(SuperNManager.get(player), -SNConfigHandler.demonPowerFireball, "Fireball!");
		ItemStack item = player.getItemInHand();
		if(item.getAmount()==1){
			player.setItemInHand(null);
		}else{
			item.setAmount(player.getItemInHand().getAmount()-1);
		}
		return true;
	}

	public boolean convert(Player player, Player target) {
		SuperNPlayer snplayer = SuperNManager.get(player);
		SuperNPlayer snvictim = SuperNManager.get(target);
		if(snplayer.getPower() < SNConfigHandler.demonConvertPower) {
			SuperNManager.sendMessage(snplayer, "Not enough power to convert!");
			return false;
		}
		if(target.getItemInHand().getType() == Material.NETHERRACK) {
			SuperNManager.alterPower(snplayer, -SNConfigHandler.demonConvertPower, "Converted " + target.getName());
			SuperNManager.convert(snvictim, "demon");
			target.sendMessage(ChatColor.RED + "Heat builds up in your body...");
			target.sendMessage(ChatColor.RED + "You have been converted to a demon by " + player.getName());
			return true;
		}
		return false;
	}

	public boolean snare(Player player, Player target){
		SuperNPlayer snplayer = SuperNManager.get(player);
		if(snplayer.getPower() < SNConfigHandler.demonPowerSnare){
			SuperNManager.sendMessage(snplayer, "Not enough power to cast snare!");
			return false;
		}
		Block block;

		if(target == null){
			block = player.getTargetBlock(null, 20);
		}else{
			block = target.getLocation().getBlock();
		}

		final Location loc = block.getLocation();

		for(int x = loc.getBlockX()-1; x < (loc.getBlockX()+2); x++){
			for(int y = loc.getBlockY()-1; y < (loc.getBlockY()+2); y++){
				for(int z = loc.getBlockZ()-1; z < (loc.getBlockZ()+2); z++){
					Location newLoc = new Location(block.getWorld(), x, y, z);
					Block newBlock = newLoc.getBlock();
					if(newBlock.getTypeId()==0){
						newBlock.setType(Material.WEB);
						webMap.put(newBlock, loc);
					}
				}
			}
		}

		SupernaturalsPlugin.instance.getServer().getScheduler().scheduleSyncDelayedTask(SupernaturalsPlugin.instance, new Runnable() {
			public void run() {
				List<Block> blocks = new ArrayList<Block>();
				for(Block block : webMap.keySet()){
					if(webMap.get(block).equals(loc)){
						block.setType(Material.AIR);
						blocks.add(block);
					}
				}
				for(Block block : blocks){
					webMap.remove(block);
					if(SNConfigHandler.debugMode)
						SupernaturalsPlugin.log("Removed web block.");
				}
			}
		}, (SNConfigHandler.demonSnareDuration/50));

		ItemStack item = player.getItemInHand();
		if(item.getAmount()==1){
			player.setItemInHand(null);
		}else{
			item.setAmount(player.getItemInHand().getAmount()-1);
		}

		SuperNManager.alterPower(SuperNManager.get(player), -SNConfigHandler.demonPowerSnare, "Snare!");
		return true;
	}

}
