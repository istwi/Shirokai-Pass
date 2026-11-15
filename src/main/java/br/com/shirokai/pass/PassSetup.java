package br.com.shirokai.pass;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Editable files are created only when absent; the legacy whole-state file is never imported. */
public final class PassSetup {
  private PassSetup(){}
  public static PassDefinition definition(Path root)throws IOException {
    Path file=root.resolve("passe.json");String text=Store.readText(file);
    if(text==null){text=Store.JSON.toJson(new PassDefinition());Store.write(file,text);}
    try{PassDefinition definition=PassDefinition.parse(text);Store.finishPending(file);return definition;}
    catch(RuntimeException e){throw new IOException("passe.json inválido; configuração ativa preservada",e);}
  }
  public static RewardBook rewards(Path root,String id)throws IOException {
    var store=Store.directory(root);RewardBook book=store.rewards(id);
    if(book==null){
      try(var stream=PassSetup.class.getResourceAsStream("/default-rewards.json")){
        if(stream==null)throw new IOException("Modelo de recompensas ausente do JAR");
        book=Store.JSON.fromJson(new String(stream.readAllBytes(),StandardCharsets.UTF_8),RewardBook.class);
      }
      book.rewards();Store.write(root.resolve("rewards/"+id+".json"),Store.JSON.toJson(book.document()));
    }
    return book;
  }
}
