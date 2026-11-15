package br.com.shirokai.pass;

import java.lang.reflect.*;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;

/** Audited Shirokai Crates 0.2.1 bridge. Reuse its live signing factory; never copy keys or elevate players. */
final class CrateRewards {
  static ItemStack prepare(String id){
    var mod=FabricLoader.getInstance().getModContainer("shirokaicrates");
    if(mod.isEmpty()||!mod.get().getMetadata().getVersion().getFriendlyString().equals("0.2.1"))
      throw new IllegalArgumentException("Chaves exigem Shirokai Crates 0.2.1 instalado");
    try{
      Object service=Class.forName("dev.shirokaicrates.ShirokaiCrates").getMethod("service").invoke(null);
      if(service==null||!Boolean.TRUE.equals(service.getClass().getMethod("healthy").invoke(service)))
        throw new IllegalArgumentException("Shirokai Crates indisponível");
      Object snapshot=service.getClass().getMethod("snapshot").invoke(service);
      Map<?,?> crates=(Map<?,?>)snapshot.getClass().getMethod("crates").invoke(snapshot);
      Object crate=crates.get(id);
      if(crate==null)throw new IllegalArgumentException("Caixa não cadastrada: "+id);
      // No public grant API exists in 0.2.1. This exact-version field holds the real token factory.
      Field field=service.getClass().getDeclaredField("tokens");field.setAccessible(true);
      Object factory=field.get(service);
      ItemStack key=(ItemStack)factory.getClass().getMethod("create",crate.getClass(),boolean.class,int.class).invoke(factory,crate,false,1);
      if(key.isEmpty())throw new IllegalArgumentException("Chave vazia: "+id);
      return key;
    }catch(ReflectiveOperationException e){
      Throwable cause=e instanceof InvocationTargetException?e.getCause():e;
      throw new IllegalArgumentException("Integração de chaves indisponível: "+cause.getMessage(),cause);
    }
  }
}
