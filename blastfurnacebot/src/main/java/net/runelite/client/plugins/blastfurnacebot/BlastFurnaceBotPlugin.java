/*
 * Copyright (c) 2018, Seth <Sethtroll3@gmail.com>
 * Copyright (c) 2019, Brandon White <bmwqg@live.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.blastfurnacebot;

import com.google.inject.Provides;

import java.awt.event.KeyEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.*;

import static net.runelite.api.NullObjectID.NULL_29330;
import static net.runelite.api.NullObjectID.NULL_9092;

import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.*;
import net.runelite.api.queries.BankItemQuery;
import net.runelite.api.util.Text;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;

import static net.runelite.api.ObjectID.*;
import static net.runelite.client.plugins.blastfurnacebot.BlastFurnaceState.*;

import net.runelite.client.plugins.botutils.BotUtils;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

@Extension
@PluginDependency(BotUtils.class)
@PluginDescriptor(
	name = "Blast Furnace - Illumine",
	description = "Illumine bot for Blast Furnace minigame",
	tags = {"minigame", "skilling", "smithing", "illumine", "bot"},
	type = PluginType.MINIGAME
)
@Slf4j
public class BlastFurnaceBotPlugin extends Plugin
{
	private static final int BAR_DISPENSER = NULL_9092;
	private static final int BF_COFFER = NULL_29330;
	private static final String FOREMAN_PERMISSION_TEXT = "Okay, you can use the furnace for ten minutes. Remember, you only need half as much coal as with a regular furnace.";
	private final List<Integer> INVENTORY_SETUP = List.of(ItemID.COAL_BAG_12019, ItemID.STAMINA_POTION1, ItemID.STAMINA_POTION2, ItemID.STAMINA_POTION3, ItemID.STAMINA_POTION4);

	@Getter(AccessLevel.PACKAGE)
	private GameObject conveyorBelt;

	@Getter(AccessLevel.PACKAGE)
	private GameObject barDispenser;

	private ForemanTimer foremanTimer;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BotOverlay overlay;

	/*@Inject
	private InfoBoxManager infoBoxManager;*/

	@Inject
	private BlastFurnaceBotConfig config;

	@Inject
	private BotUtils utils;

	BlastFurnaceState state;
	MenuEntry targetMenu;
	LocalPoint beforeLoc = new LocalPoint(0, 0); //initiate to mitigate npe, this sucks
	Instant botTimer;

	private int timeout = 0;
	private boolean coalBagFull;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		coalBagFull = false;
		timeout = 0;
		targetMenu = null;
		botTimer = Instant.now();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		//infoBoxManager.removeIf(ForemanTimer.class::isInstance);
		conveyorBelt = null;
		barDispenser = null;
		foremanTimer = null;
	}

	private void openBank()
	{
		GameObject bankObject = utils.findNearestGameObject(26707);
		if (bankObject != null)
		{
			targetMenu = new MenuEntry("", "", bankObject.getId(), MenuOpcode.GAME_OBJECT_FIRST_OPTION.getId(), bankObject.getSceneMinLocation().getX(), bankObject.getSceneMinLocation().getY(), true);
			utils.clickRandomPointCenter(-100, 100);
			timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
		}
	}

	private void putConveyorBelt()
	{
		targetMenu = new MenuEntry("", "", conveyorBelt.getId(), MenuOpcode.GAME_OBJECT_FIRST_OPTION.getId(), conveyorBelt.getSceneMinLocation().getX(), conveyorBelt.getSceneMinLocation().getY(), false);
		utils.sleep(10, 100);
		utils.clickRandomPointCenter(-100, 100);
		timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
	}

	private void collectFurnace()
	{
		log.info("At collectFurnace(), collecting bars");
		targetMenu = new MenuEntry("", "", barDispenser.getId(), MenuOpcode.GAME_OBJECT_FIRST_OPTION.getId(), barDispenser.getSceneMinLocation().getX(), barDispenser.getSceneMinLocation().getY(), false);
		utils.clickRandomPointCenter(-100, 100);
		timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
	}

	private void fillCoalBag(WidgetItem coalBag)
	{
		targetMenu = new MenuEntry("", "", coalBag.getId(), MenuOpcode.ITEM_FIRST_OPTION.getId(), coalBag.getIndex(), 9764864, false);
		utils.sleep(25, 100);
		utils.clickRandomPointCenter(-100, 100);
	}

	private void emptyCoalBag(WidgetItem coalBag)
	{
		targetMenu = new MenuEntry("", "", coalBag.getId(), MenuOpcode.ITEM_FOURTH_OPTION.getId(), coalBag.getIndex(), 9764864, false);
		utils.sleep(25, 100);
		utils.clickRandomPointCenter(-100, 100);
	}

	private BlastFurnaceState collectBars()
	{
		if (utils.getInventorySpace() < 26)
		{
			log.info("collect bars but need inventory space first");
			openBank();
			return OPENING_BANK;
		}
		collectFurnace();
		return COLLECTING_BARS;
	}

	private BlastFurnaceState getState()
	{
		if (conveyorBelt == null || barDispenser == null)
		{
			conveyorBelt = utils.findNearestGameObject(CONVEYOR_BELT);
			barDispenser = utils.findNearestGameObject(BAR_DISPENSER);
			if (conveyorBelt == null || barDispenser == null)
			{
				return OUT_OF_AREA;
			}
		}
		if (timeout > 0)
		{
			utils.handleRun(10, 30);
			return TIMEOUT;
		}
		if (utils.isMoving(beforeLoc))
		{
			timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
			return MOVING;
		}
		if (!utils.isBankOpen())
		{
			if (!client.getWidget(162, 40).isHidden()) //deposit amount for coffer widget
			{
				if (!utils.inventoryContains(ItemID.COINS_995, config.cofferAmount()))
				{
					openBank();
					return OPENING_BANK;
				}
				int randDepositAmount = utils.getRandomIntBetweenRange(config.cofferAmount(), 10000);
				int depositAmount = (utils.inventoryContains(ItemID.COINS_995, randDepositAmount)) ? randDepositAmount : config.cofferAmount();

				utils.typeString(String.valueOf(depositAmount));
				utils.sleep(10, 50);
				utils.pressKey(KeyEvent.VK_ENTER);
				utils.sleep(200, 350);
				return FILL_COFFER;
			}
			utils.handleRun(20, 20);
			if(utils.inventoryContains(ItemID.RUNITE_BAR)) //TODO: update botutils to take a String contains, so can search if inventory has any Bars
			{ //INVENTORY CONTAINS BARS
				openBank();
				return OPENING_BANK;
			}
			if (client.getVar(Varbits.BAR_DISPENSER) > 0) //BARS IN FURNACE
			{
				if (utils.getInventorySpace() < 26)
				{
					openBank();
					return OPENING_BANK;
				}
				collectFurnace();
				return COLLECTING_BARS;
			}
			if (client.getVar(Varbits.BLAST_FURNACE_COFFER) < config.cofferThreshold())
			{
				if (utils.inventoryContains(ItemID.COINS_995, config.cofferAmount()))
				{
					//TODO handle filling up coffer
					GameObject coffer = utils.findNearestGameObject(BF_COFFER);
					if (coffer != null)
					{
						targetMenu = new MenuEntry("", "", coffer.getId(), MenuOpcode.GAME_OBJECT_FIRST_OPTION.getId(), coffer.getSceneMinLocation().getX(), coffer.getSceneMinLocation().getY(), false);
						utils.sleep(50, 250);
						utils.clickRandomPointCenter(-100, 100);
						timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
					}
					else
					{
						utils.sendGameMessage("Coffer is null, wrong world?");
					}
					return FILL_COFFER;
				}
				else
				{
					openBank();
					return OPENING_BANK;
				}
			}
			GameObject bank = utils.findNearestGameObject(BANK_CHEST_26707);
			if (bank != null)
			{
				WidgetItem coalBag = utils.getInventoryWidgetItem(ItemID.COAL_BAG_12019);
				if (client.getLocalPlayer().getWorldLocation().distanceTo(bank.getWorldLocation()) < 8) //At bank location
				{
					if (utils.getItems(List.of(ItemID.COAL, ItemID.RUNITE_ORE)).isEmpty()) //Inventory does not contain coal or runite ore
					{
						openBank();
						return OPENING_BANK;
					}
					if (coalBag != null)
					{
						if (!coalBagFull)
						{
							if (utils.inventoryContains(ItemID.COAL))
							{
								fillCoalBag(coalBag);
							}
							if (utils.inventoryContains(ItemID.RUNITE_ORE)) //shouldn't happen
							{
								putConveyorBelt();
								return PUT_CONVEYOR_BELT;
							}
						}
						if (coalBagFull)
						{
							if (!utils.getItems(List.of(ItemID.COAL, ItemID.RUNITE_ORE)).isEmpty())
							{
								putConveyorBelt();
								return PUT_CONVEYOR_BELT;
							}
						}
					} //TODO handle not having a coal bag
				}
				else //Not near bank chest, assume near conveyor belt
				{
					if (utils.getItems(List.of(ItemID.COAL, ItemID.RUNITE_ORE)).isEmpty())
					{
						if (!coalBagFull)
						{
							utils.sleep(60, 250);
							if (client.getVar(Varbits.BAR_DISPENSER) > 0)
							{
								collectFurnace();
								return COLLECTING_BARS;
							}
							else
							{
								openBank();
								return OPENING_BANK;
							}
						}
						if (coalBagFull)
						{
							emptyCoalBag(coalBag);
						}
					}
					if (!utils.getItems(List.of(ItemID.COAL, ItemID.RUNITE_ORE)).isEmpty())
					{
						putConveyorBelt();
						if (!coalBagFull)
						{
							timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
							return PUT_CONVEYOR_BELT;
						}
					}
				}
			}
		}
		else if (utils.isBankOpen()) //redundant but doing for readability
		{
			WidgetItem runiteBar = utils.getInventoryWidgetItem(ItemID.RUNITE_BAR);
			if (runiteBar != null) //TODO: Make into a function and make compatible with all bars
			{
				log.info("depositing runite bar");
				utils.depositAllExcept(INVENTORY_SETUP);
				return DEPOSITING;
			}
			if (client.getVar(Varbits.BAR_DISPENSER) > 0) //Bars in dispenser
			{
				log.info("bars ready for collection, bank is open, depositing inventory and collecting");
				if (utils.getInventorySpace() < 26 ) //TODO: create inventoryContainsExcept method in utils
				{
					utils.depositAllExcept(INVENTORY_SETUP);
					return DEPOSITING;
				}
				return collectBars();
			}
			if (client.getVar(Varbits.BLAST_FURNACE_COFFER) < config.cofferThreshold())
			{
				if (utils.inventoryContains(ItemID.COINS_995, config.cofferAmount()))
				{
					utils.closeBank();
					return FILL_COFFER;
				}
				if (utils.inventoryFull() && !utils.inventoryContains(ItemID.COINS_995))
				{
					log.info("Depositing inventory to make room for coins for coffer");
					utils.depositAllExcept(INVENTORY_SETUP);
					return DEPOSITING;
				}
				if (utils.bankContains(ItemID.COINS_995, config.cofferAmount()))
				{
					Widget bankCoins = utils.getBankItemWidget(ItemID.COINS_995);
					utils.withdrawAllItem(bankCoins);
					return FILL_COFFER;
				}
				else
				{
					utils.sendGameMessage("Out of coins");
					utils.closeBank();
					utils.sendGameMessage("Log Off.");
					return OUT_OF_ITEMS;
				}
			}
            /*WidgetItem emptyVial = utils.getInventoryWidgetItem(ItemID.VIAL);
            if (emptyVial != null) {
                log.info("depositing empty vial");
				utils.depositAllOfItem(emptyVial);
                return DEPOSITING;
            }*/
			Widget staminaPotionBank = utils.getBankItemWidgetAnyOf(ItemID.STAMINA_POTION1, ItemID.STAMINA_POTION2, ItemID.STAMINA_POTION3, ItemID.STAMINA_POTION4);
			if (staminaPotionBank != null && utils.getItems(List.of(ItemID.STAMINA_POTION1, ItemID.STAMINA_POTION2, ItemID.STAMINA_POTION3, ItemID.STAMINA_POTION4)).isEmpty())
			{
                /*if (utils.inventoryFull()) {
                    log.info("depositing inventory to make room for stamina pot");
					utils.depositAllExcept(INVENTORY_SETUP);
					return DEPOSITING;
                }*/
				utils.depositAllExcept(INVENTORY_SETUP);
				log.info("withdrawing stam pot");
				utils.withdrawItem(staminaPotionBank);
				return WITHDRAWING;
			}
			if (!utils.inventoryContains(ItemID.COAL_BAG_12019))
			{
				Widget coalBagBank = utils.getBankItemWidget(ItemID.COAL_BAG_12019);
				if (coalBagBank != null)
				{
					utils.depositAllExcept(INVENTORY_SETUP);
					log.info("withdrawing coal bag");
					utils.withdrawItem(coalBagBank);
					return WITHDRAWING;
				}
				else
				{
					utils.sendGameMessage("We don't have a coal bag!");
					utils.closeBank();
					utils.sendGameMessage("Log Off.");
					return OUT_OF_ITEMS;
				}
			}
			if (client.getVar(BarsOres.COAL.getVarbit()) < 81 || !coalBagFull)
			{
				if (utils.inventoryContains(ItemID.COAL))
				{
					if (!coalBagFull)
					{
						utils.closeBank(); //filling handled in bank not open logic
						return FILL_COAL_BAG;
					}
					else
					{
						putConveyorBelt();
						return PUT_CONVEYOR_BELT;
					}
				}
				if (utils.inventoryFull()) //TODO: actually handle this properly
				{
					utils.depositAllExcept(INVENTORY_SETUP);
					utils.sendGameMessage("inventory is full but need to withdraw coal");
					return OUT_OF_ITEMS;
				}
				Widget coalBank = utils.getBankItemWidget(ItemID.COAL);
				if (coalBank != null)
				{
					utils.depositAllExcept(INVENTORY_SETUP);
					log.info("withdrawing coal");
					utils.withdrawAllItem(coalBank);
					log.info("sleeping");
					return WITHDRAWING; //This might be the wrong return
				}
				else
				{
					utils.sendGameMessage("out of coal, log off.");
					return OUT_OF_ITEMS;
				}
			}
			if (client.getVar(BarsOres.COAL.getVarbit()) >= 81 && coalBagFull) //logic probably needs updating
			{
				if (utils.inventoryFull() || utils.inventoryContains(ItemID.RUNITE_ORE))
				{
					if (utils.inventoryContains(ItemID.RUNITE_ORE))
					{
						log.info("putting runite ore onto belt");
						putConveyorBelt();
						return PUT_CONVEYOR_BELT;
					}
					else
					{
						utils.depositAllExcept(INVENTORY_SETUP);
						utils.sendGameMessage("need to withdraw runite ore but inventory is full, something went wrong.");
						return OUT_OF_ITEMS;
					}
				}
				Widget bankRuniteOre = utils.getBankItemWidget(ItemID.RUNITE_ORE);
				if (bankRuniteOre != null)
				{
					utils.depositAllExcept(INVENTORY_SETUP);
					log.info("withdrawing runite ore");
					utils.withdrawAllItem(bankRuniteOre);
					timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
					return WITHDRAWING;
				}
				else
				{
					utils.closeBank();
					utils.sendGameMessage("Out of Runite ore, log off");
					return OUT_OF_ITEMS;
				}
			}
		}
		return null;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (targetMenu == null)
		{
			log.info("Modified MenuEntry is null");
			return;
		}
		//TODO: build this into utils or use random handler getter?
		if (utils.getRandomEvent()) //for random events
		{
			log.info("Blast furnace bot not overriding click due to random event");
			return;
		}
		if (targetMenu.getIdentifier() == ItemID.COAL_BAG_12019)
		{
			if (targetMenu.getOpcode() == MenuOpcode.ITEM_FIRST_OPTION.getId())
			{
				coalBagFull = true;
			}
			if (targetMenu.getOpcode() == MenuOpcode.ITEM_FOURTH_OPTION.getId())
			{
				coalBagFull = false;
			}
		}
		log.info("inserting menu at MOC event: " + targetMenu.toString());
		event.setMenuEntry(targetMenu);
		timeout = utils.getRandomIntBetweenRange(1,config.delayAmount());
		targetMenu = null; //this allow the player to interact with the client without their clicks being overridden
	}

	@Provides
	BlastFurnaceBotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlastFurnaceBotConfig.class);
	}

	@Subscribe
	private void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject gameObject = event.getGameObject();

		switch (gameObject.getId())
		{
			case CONVEYOR_BELT:
				conveyorBelt = gameObject;
				break;

			case BAR_DISPENSER:
				barDispenser = gameObject;
				break;
		}
	}

	@Subscribe
	private void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject gameObject = event.getGameObject();

		switch (gameObject.getId())
		{
			case CONVEYOR_BELT:
				conveyorBelt = null;
				break;

			case BAR_DISPENSER:
				barDispenser = null;
				break;
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			conveyorBelt = null;
			barDispenser = null;
		}
	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != WidgetID.MULTISKILL_MENU_GROUP_ID && event.getGroupId() != WidgetID.CHATBOX_GROUP_ID && event.getGroupId() != WidgetID.DIALOG_OPTION_GROUP_ID)
		{
			return;
		}
		//Collect Bars
		if (event.getGroupId() == WidgetID.MULTISKILL_MENU_GROUP_ID)
		{
			targetMenu = new MenuEntry("", "", 1, 57, -1, 17694734, false); //Take Runite Bar from Bar Dispenser
			utils.clickRandomPointCenter(-100, 100);
			return;
		}
		//Deposit coins widget option
		if (event.getGroupId() == WidgetID.DIALOG_OPTION_GROUP_ID)
		{
			targetMenu = new MenuEntry("", "", 0, 30, 1, 14352385, false); //Take Runite Bar from Bar Dispenser
			utils.clickRandomPointCenter(-100, 100);
			return;
		}
	}

	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (client != null && client.getLocalPlayer() != null && client.getGameState() == GameState.LOGGED_IN)
		{
			if (!utils.iterating)
			{
				state = getState();
				beforeLoc = client.getLocalPlayer().getLocalLocation();
				if (state != null)
				{
					log.info(state.name());
					switch (state)
					{
						case TIMEOUT:
							timeout--;
							return;
					}
				}
				else
				{
					log.info("state is null");
				}
		/*Widget npcDialog = client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
		if (npcDialog == null)
		{
			return;
		}

		// blocking dialog check until 5 minutes needed to avoid re-adding while dialog message still displayed
		boolean shouldCheckForemanFee = client.getRealSkillLevel(Skill.SMITHING) < 60
			&& (foremanTimer == null || Duration.between(Instant.now(), foremanTimer.getEndTime()).toMinutes() <= 5);

		if (shouldCheckForemanFee)
		{
			String npcText = Text.sanitizeMultilineText(npcDialog.getText());

			if (npcText.equals(FOREMAN_PERMISSION_TEXT))
			{
				infoBoxManager.removeIf(ForemanTimer.class::isInstance);

				foremanTimer = new ForemanTimer(this, itemManager);
				infoBoxManager.addInfoBox(foremanTimer);
			}
		}*/
			}
			else
			{
				log.info("utils is iterating");
			}
		}
	}
}