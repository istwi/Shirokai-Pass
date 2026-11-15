package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import static net.minecraft.server.command.CommandManager.*;

/** Real signed keys, ordinary player, durable claims and failed preflight without partial prizes. */
public final class CrateAuditHarness implements ModInitializer {
  public void onInitialize(){
    if(!Boolean.getBoolean("shirokai.audit"))return;
    CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("auditcrate").requires(s->s.hasPermissionLevel(4))
      .then(argument("player",EntityArgumentType.player()).executes(c->{
        try{
          var player=EntityArgumentType.getPlayer(c,"player");var mod=ShirokaiPass.instance();
          if(c.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile()))throw new AssertionError("Fixture player must not be OP");
          mod.service.create("crate_test",86400000,0,new Config());
          mod.service.edit(state->{
            var first=state.seasons.get("crate_test").rewards.get("free:1");first.enabled=true;first.choices=List.of(new Choice("crate","shiny1",3,1));
            var second=state.seasons.get("crate_test").rewards.get("free:2");second.enabled=true;second.choices=List.of(new Choice("command","give {player} minecraft:emerald 5",1,1),new Choice("crate","not_registered",1,1));
          });
          mod.service.add(player.getUuid(),225,null,false);mod.claim(player,"crate_test",List.of("free:1"));
          check(countKeys(player)==3,"Three authentic crate keys were not delivered");
          mod.claim(player,"crate_test",List.of("free:1"));check(countKeys(player)==3,"Repeated claim duplicated keys");
          mod.claim(player,"crate_test",List.of("free:2"));
          check(countKeys(player)==3,"Invalid crate changed prior keys");
          for(int index=0;index<36;index++)check(!player.getInventory().getStack(index).isOf(net.minecraft.item.Items.EMERALD),"Failed crate preflight executed money/item command");
          check(mod.service.snapshot().deliveries.values().stream().anyMatch(d->d.reward.equals("free:2")&&d.status.equals("READY")),"Missing crate did not stay pending");
          check(!c.getSource().getServer().getPlayerManager().isOperator(player.getGameProfile()),"Delivery elevated recipient");
          c.getSource().sendFeedback(()->Text.literal("CRATE_PASS_SMOKE_OK: signed keys, ordinary player, no duplication, no partial prizes"),false);return 1;
        }catch(Exception|AssertionError e){throw new RuntimeException("CRATE_PASS_SMOKE_FAIL",e);}
      }))));
  }
  private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  private static int countKeys(ServerPlayerEntity player)throws Exception{
    Object service=Class.forName("dev.shirokaicrates.ShirokaiCrates").getMethod("service").invoke(null);
    var field=service.getClass().getDeclaredField("tokens");field.setAccessible(true);Object factory=field.get(service);
    Object snapshot=service.getClass().getMethod("snapshot").invoke(service);Object crate=((Map<?,?>)snapshot.getClass().getMethod("crates").invoke(snapshot)).get("shiny1");
    var matches=factory.getClass().getMethod("matches",ItemStack.class,crate.getClass(),boolean.class);int count=0;
    for(int index=0;index<36;index++){ItemStack stack=player.getInventory().getStack(index);if(Boolean.TRUE.equals(matches.invoke(factory,stack,crate,false)))count+=stack.getCount();}
    return count;
  }
}
