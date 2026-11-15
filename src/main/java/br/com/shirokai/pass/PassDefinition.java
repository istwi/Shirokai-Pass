package br.com.shirokai.pass;

/** Change timerRevision to restart the timer once; restarting the server never restarts it. */
public final class PassDefinition {
  public int schema=1;
  public boolean autoStart=true;
  public String id="shirokai_01", name="Passe Shirokai — Temporada 1", timerRevision="1";
  public int durationDays=90, graceDays=7;
  public long baseXp=100, incrementXp=25;
  public static PassDefinition parse(String text){
    var json=com.google.gson.JsonParser.parseString(text).getAsJsonObject();
    var allowed=java.util.Set.of("schema","autoStart","id","name","timerRevision","durationDays","graceDays","baseXp","incrementXp");
    for(var entry:json.entrySet()){
      String key=entry.getKey();var value=entry.getValue();
      if(!allowed.contains(key))throw new IllegalArgumentException("Campo desconhecido em passe.json: "+key);
      if(!value.isJsonPrimitive())throw new IllegalArgumentException("Valor inválido: "+key);
      if(key.equals("autoStart")){if(!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("autoStart deve ser true/false");}
      else if(java.util.Set.of("id","name","timerRevision").contains(key)){if(!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" deve ser texto");}
      else if(!value.toString().matches("[0-9]{1,18}"))throw new IllegalArgumentException(key+" deve ser inteiro positivo");
    }
    if(!json.has("schema"))throw new IllegalArgumentException("schema obrigatório em passe.json");
    PassDefinition result=Store.JSON.fromJson(json,PassDefinition.class);result.validate();return result;
  }
  public void validate(){
    if(schema!=1||id==null||!id.matches("[a-zA-Z0-9_-]{1,64}")||name==null||name.isBlank()||name.length()>128
       ||timerRevision==null||!timerRevision.matches("[a-zA-Z0-9_-]{1,64}")||durationDays<1||durationDays>3650||graceDays<0||graceDays>365)
      throw new IllegalArgumentException("passe.json inválido: confira id, nome, dias e timerRevision");
    Model.threshold(100,baseXp,incrementXp);
  }
  public long duration(){return Math.multiplyExact(durationDays,86_400_000L);}
  public long grace(){return Math.multiplyExact(graceDays,86_400_000L);}
}
