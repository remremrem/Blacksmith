package net.apunch.blacksmith;

import java.util.logging.Level;

import net.apunch.blacksmith.util.Settings;
import net.apunch.blacksmith.util.Settings.Setting;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.util.DataKey;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import regalowl.hyperconomy.APIBridge;


public class BlacksmithPlugin extends JavaPlugin {
	private Settings config;
	private Economy economy;
	private APIBridge hyperAPI;
	private boolean useHyperAPI = false;
        //private boolean hasCititrader = false; // CitiTrader dependency outdated and broken

	@Override
	public void onDisable() {
	//	config.save();

		getLogger().log(Level.INFO, " v" + getDescription().getVersion() + " disabled.");
	}

	@Override
	public void onEnable() {
		config = new Settings(this);
		config.load();

		// Setup Hyperconomy (Soft-Depend only, so this is completely optional!)    
		// Hyperconomy uses your favorite Vault-compatible economy system
		// and calculates prices for items based on supply and demand on the fly.
		// This is only used to get the cost of a repair.
		if (Bukkit.getPluginManager().getPlugin("HyperConomy") != null) {
			getServer().getLogger().log(Level.INFO, "Found HyperConomy! Using that for calculating prices, base-prices and price-per-durability-point in the Blacksmith config.yml will NOT be used!");
			this.useHyperAPI = true;
			this.hyperAPI = new APIBridge();
		}

        /* CitiTrader dependency outdated and broken
                // Check for Cititrader
                 if(getServer().getPluginManager().getPlugin("CitiTrader") != null) {
                     hasCititrader = true;
                 }
                 */
                
		// Setup Vault
		RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(
				Economy.class);
		if (economyProvider != null)
			economy = economyProvider.getProvider();
		else {
			// Disable if no economy plugin was found
			getServer().getLogger().log(Level.SEVERE, "Failed to load an economy plugin. Disabling...");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(BlacksmithTrait.class).withName("blacksmith"));


		getLogger().log(Level.INFO, " v" + getDescription().getVersion() + " enabled.");
	}

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        config.load();
        sender.sendMessage(ChatColor.GREEN + "Blacksmith config reloaded!");
        return true;
    }

    /* CitiTrader dependency outdated and broken
    // Return if we have cititrader
         public boolean hasCititrader() {
            return this.hasCititrader;
         }
         */
        
	public BlacksmithTrait getBlacksmith(NPC npc){

		if (npc !=null && npc.hasTrait(BlacksmithTrait.class)){
			return npc.getTrait(BlacksmithTrait.class);
		}

		return null;
	}


	public boolean isTool(ItemStack item) {
		switch (item.getType()) {
		case WOOD_PICKAXE:
		case WOOD_SPADE:
		case WOOD_HOE:
		case WOOD_SWORD:
		case WOOD_AXE:
		case STONE_PICKAXE:
		case STONE_SPADE:
		case STONE_HOE:
		case STONE_SWORD:
		case STONE_AXE:
		case GOLD_PICKAXE:
		case GOLD_SPADE:
		case GOLD_HOE:
		case GOLD_SWORD:
		case GOLD_AXE:
		case IRON_PICKAXE:
		case IRON_SPADE:
		case IRON_HOE:
		case IRON_SWORD:
		case IRON_AXE:
		case DIAMOND_PICKAXE:
		case DIAMOND_SPADE:
		case DIAMOND_HOE:
		case DIAMOND_SWORD:
		case DIAMOND_AXE:
		case BOW:
		case FLINT_AND_STEEL:
		case FISHING_ROD:
		case SHEARS:
			return true;
		default:
			return false;
		}
	}

	public boolean isArmor(ItemStack item) {
		switch (item.getType()) {
		case LEATHER_HELMET:
		case LEATHER_CHESTPLATE:
		case LEATHER_LEGGINGS:
		case LEATHER_BOOTS:
		case CHAINMAIL_HELMET:
		case CHAINMAIL_CHESTPLATE:
		case CHAINMAIL_LEGGINGS:
		case CHAINMAIL_BOOTS:
		case GOLD_HELMET:
		case GOLD_CHESTPLATE:
		case GOLD_LEGGINGS:
		case GOLD_BOOTS:
		case IRON_HELMET:
		case IRON_CHESTPLATE:
		case IRON_LEGGINGS:
		case IRON_BOOTS:
		case DIAMOND_HELMET:
		case DIAMOND_CHESTPLATE:
		case DIAMOND_LEGGINGS:
		case DIAMOND_BOOTS:
			return true;
		default:
			return false;
		}
	}

	public boolean doesPlayerHaveEnough(Player player) {
		return economy.getBalance(player.getName()) - getCost(player.getItemInHand()) >= 0;
	}

	public String formatCost(Player player) {
		return economy.format(getCost(player.getItemInHand()));
	}

	public void withdraw(Player player) {
		economy.withdrawPlayer(player.getName(), getCost(player.getItemInHand()));
	}
       /* CitiTrader dependency outdated and broken.
        public void deposit(NPC npc, Player player) {
            if(hasCititrader) {
                if(npc.hasTrait(WalletTrait.class)) {
                    npc.getTrait(WalletTrait.class).deposit(getCost(player.getItemInHand()));
                }
            }
        }
        */

	private double getCost(ItemStack item) {
		DataKey root = config.getConfig().getKey("");
		double price = Setting.BASE_PRICE.asDouble();
		if (root.keyExists("base-prices." + item.getType().name().toLowerCase().replace('_', '-')))
			price = root.getDouble("base-prices." + item.getType().name().toLowerCase().replace('_', '-'));

		// Adjust price based on durability and enchantments
		if (this.useHyperAPI) {
			// If using hyperconomy, price is calculated like so:
			// New Item Price (from hyperconomy) / maxDurability = price per durability point
			// Total price would then be base_price + price per durablity point * current durability
			double hyperPrice = this.hyperAPI.getItemPurchasePrice(item.getTypeId(), 0, item.getAmount());
			double hyperPricePerDurability = hyperPrice / item.getType().getMaxDurability();
			price += (item.getDurability() * hyperPricePerDurability);
		}

		else {
			if (root.keyExists("price-per-durability-point." + item.getType().name().toLowerCase().replace('_', '-')))
				price += item.getDurability() * root.getDouble("price-per-durability-point." + item.getType().name().toLowerCase().replace('_', '-'));
			else price += (item.getDurability() * Setting.PRICE_PER_DURABILITY_POINT.asDouble());
		}

		double enchantmentModifier = Setting.ENCHANTMENT_MODIFIER.asDouble();
		for (Enchantment enchantment : item.getEnchantments().keySet()) {
			if (root.keyExists("enchantment-modifiers." + enchantment.getName().toLowerCase().replace('_', '-')))
				enchantmentModifier = root.getDouble("enchantment-modifiers."
						+ enchantment.getName().toLowerCase().replace('_', '-'));
			price += enchantmentModifier * item.getEnchantmentLevel(enchantment);
		}
		return price;
	}
}