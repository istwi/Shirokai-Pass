package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import java.util.*;

/** Human editable catalog, completely separate from progress/claim receipts. */
public final class RewardBook {
  public int schema = 1;
  public Map<String, Reward> free = new LinkedHashMap<>(), premium = new LinkedHashMap<>();
  public Map<String, Reward> rewards() {
    if (schema != 1 || free == null || premium == null) throw new IllegalArgumentException("Arquivo de recompensas inválido");
    var result = new LinkedHashMap<String, Reward>();
    add(result, free, false); add(result, premium, true);
    if (result.size() != 200) throw new IllegalArgumentException("Defina os 100 níveis grátis e os 100 Premium");
    return result;
  }
  private void add(Map<String,Reward> result, Map<String,Reward> track, boolean paid) {
    for(var entry:track.entrySet()) {
      int level = Integer.parseInt(entry.getKey());
      if(!entry.getKey().equals(Integer.toString(level)))throw new IllegalArgumentException("Nível inválido");
      Reward reward = Store.JSON.fromJson(Store.JSON.toJson(entry.getValue()), Reward.class);
      if(reward==null)throw new IllegalArgumentException("Recompensa nula");
      reward.level=level; reward.premium=paid; reward.validate(); result.put(key(level,paid),reward);
    }
  }
  public com.google.gson.JsonObject document(){
    var json=Store.JSON.toJsonTree(this).getAsJsonObject();
    for(String track:List.of("free","premium"))for(var entry:json.getAsJsonObject(track).entrySet()){
      entry.getValue().getAsJsonObject().remove("level");entry.getValue().getAsJsonObject().remove("premium");
    }
    return json;
  }
  public static RewardBook from(Season season) {
    RewardBook book=new RewardBook();
    for(var reward:season.rewards.values())(reward.premium?book.premium:book.free).put(Integer.toString(reward.level),reward);
    return book;
  }
}
