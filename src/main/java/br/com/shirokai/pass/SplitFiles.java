package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Redo transaction spanning progress and changed catalogs. Normal XP saves never rewrite rewards. */
final class SplitFiles {
  final Path root, data, journal;
  final AtomicFiles.IO io;
  final Map<String,JsonElement> savedBooks=new HashMap<>();
  boolean observed;
  SplitFiles(Path root,AtomicFiles.IO io){this.root=AtomicFiles.absolute(root);this.io=io;data=this.root.resolve("data/players.json");journal=this.root.resolve("data/commit.json");}
  Path bookPath(String id){if(!id.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("ID inválido");return root.resolve("rewards/"+id+".json");}
  RewardBook book(String id)throws IOException{
    String text=Store.readText(bookPath(id));
    if(text==null)return null;
    try{RewardBook book=Store.JSON.fromJson(text,RewardBook.class);book.rewards();return book;}
    catch(RuntimeException e){throw new IOException("Recompensas inválidas em "+bookPath(id),e);}
  }
  private void replay()throws IOException{
    var pending=AtomicFiles.read(journal);if(pending==null)return;
    try{
      var tx=JsonParser.parseString(pending.text()).getAsJsonObject();
      if(tx.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Transação desconhecida");
      var files=tx.getAsJsonObject("files");var previous=tx.getAsJsonObject("previous");
      if(previous==null||!previous.keySet().equals(files.keySet()))throw new IllegalArgumentException("Transação incompleta");
      // Validate every target and payload before writing anything.
      for(var e:files.entrySet()){
        if(!e.getKey().equals("data/players.json")&&!e.getKey().matches("rewards/[a-zA-Z0-9_-]{1,64}\\.json"))throw new IllegalArgumentException("Destino inválido");
        if(!e.getValue().isJsonObject())throw new IllegalArgumentException("Documento inválido");
        if(e.getKey().startsWith("rewards/"))Store.JSON.fromJson(e.getValue(),RewardBook.class).rewards();
        else Store.validate(e.getValue().toString(),false);
      }
      for(var e:files.entrySet()){
        String current=Store.readText(root.resolve(e.getKey()));
        JsonElement value=current==null?new JsonPrimitive("ABSENT"):JsonParser.parseString(current);
        if(!value.equals(previous.get(e.getKey()))&&!value.equals(e.getValue()))
          throw new IOException("Arquivo alterado fora da transação: "+root.resolve(e.getKey())+". Conteúdo preservado; finalize recuperação antes de editar.");
      }
      AtomicFiles.complete(journal,io);
      for(var e:files.entrySet()){
        Path target=root.resolve(e.getKey());AtomicFiles.complete(target,io);AtomicFiles.write(target,Store.JSON.toJson(e.getValue()),io);
      }
      Files.delete(journal);io.syncDirectory(journal.getParent());
    }catch(RuntimeException e){throw new IOException("Transação inválida; arquivos preservados em "+journal,e);}
  }
  String load()throws IOException{
    replay();String raw=Store.readText(data);
    if(raw==null){if(observed)throw new IOException("Dados desapareceram durante execução; reinicie após reset intencional");return null;}
    observed=true;
    State state=Store.validate(raw,false);
    for(Season season:state.seasons.values()){
      RewardBook book=book(season.id);if(book==null)throw new IOException("Arquivo de recompensas ausente: "+bookPath(season.id));
      season.rewards=book.rewards();savedBooks.put(season.id,book.document());
    }
    AtomicFiles.complete(data,io);
    return Store.JSON.toJson(state);
  }
  void save(State state)throws IOException{
    if(AtomicFiles.read(journal)!=null)throw new IOException("Transação pendente; recupere armazenamento");
    if(observed&&Store.readText(data)==null)throw new IOException("Dados removidos em execução; nada sobrescrito");
    var payload=Store.JSON.toJsonTree(state).getAsJsonObject();
    var files=new JsonObject();var books=new HashMap<String,JsonElement>();
    for(Season season:state.seasons.values()){
      var book=RewardBook.from(season).document();books.put(season.id,book);
      if(!book.equals(savedBooks.get(season.id)))files.add("rewards/"+season.id+".json",book);
      payload.getAsJsonObject("seasons").getAsJsonObject(season.id).add("rewards",new JsonObject());
    }
    files.add("data/players.json",payload);
    var previous=new JsonObject();
    for(String target:files.keySet()){
      String current=Store.readText(root.resolve(target));previous.add(target,current==null?new JsonPrimitive("ABSENT"):JsonParser.parseString(current));
    }
    var tx=new JsonObject();tx.addProperty("schema",1);tx.add("files",files);tx.add("previous",previous);
    AtomicFiles.write(journal,Store.JSON.toJson(tx),io);replay();savedBooks.clear();savedBooks.putAll(books);observed=true;
  }
}
