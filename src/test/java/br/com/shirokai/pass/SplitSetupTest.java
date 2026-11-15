package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.*;

class SplitSetupTest {
  @TempDir Path root;
  AtomicLong now=new AtomicLong(1_000_000L);
  PassService service;
  PassDefinition definition;
  RewardBook book;
  UUID player=UUID.randomUUID();
  @BeforeEach void setup()throws Exception{
    definition=PassSetup.definition(root);book=PassSetup.rewards(root,definition.id);
    service=new PassService(Store.directory(root),now::get);service.configure(definition,book);
  }
  @Test void rewardsAreSeparateAndManualEditsSurviveXpWrites()throws Exception{
    String data=Files.readString(root.resolve("data/players.json"));
    assertFalse(data.contains("eco deposit"));assertFalse(data.contains("minecraft:iron_ingot"));
    Path rewards=root.resolve("rewards/"+definition.id+".json");
    var edited=Store.JSON.fromJson(Files.readString(rewards),RewardBook.class);
    edited.free.get("1").choices.getFirst().count=12;
    String raw=Store.JSON.toJson(edited.document());Files.writeString(rewards,raw);
    service.add(player,100,null,false);assertEquals(raw,Files.readString(rewards));
    service.configure(definition,Store.directory(root).rewards(definition.id));
    assertEquals(12,service.snapshot().seasons.get(definition.id).rewards.get("free:1").choices.getFirst().count);
    var restarted=new PassService(Store.directory(root),now::get);assertEquals(100,restarted.progress(definition.id,player).xp);
  }
  @Test void timerIsStableAcrossReloadRestartAndResetsOnlyOnRevisionChange()throws Exception{
    long start=service.active().start,end=service.active().end;
    service.add(player,150,null,false);now.addAndGet(5000);service.configure(definition,book);
    assertEquals(start,service.active().start);assertEquals(end,service.active().end);
    service=new PassService(Store.directory(root),now::get);service.configure(definition,book);assertEquals(end,service.active().end);
    definition.timerRevision="2";service.configure(definition,book);assertEquals(now.get(),service.active().start);assertEquals(150,service.progress(definition.id,player).xp);
    definition.durationDays+=7;service.configure(definition,book);assertEquals(now.get()+definition.duration(),service.active().end);
    service.end(definition.id);service.configure(definition,book);assertFalse(service.snapshot().seasons.get(definition.id).earning(now.get()));
  }
  @Test void resetDataOfflineKeepsCatalogAndNewIdStartsAtZero()throws Exception{
    service.add(player,150,null,false);
    Path rewards=root.resolve("rewards/"+definition.id+".json");String original=Files.readString(rewards);
    Files.delete(root.resolve("data/players.json"));
    var restarted=new PassService(Store.directory(root),now::get);restarted.configure(definition,PassSetup.rewards(root,definition.id));
    assertEquals(0,restarted.progress(definition.id,player).xp);assertEquals(original,Files.readString(rewards));
    definition.id="nova";restarted.configure(definition,book);assertEquals("nova",restarted.active().id);assertEquals(0,restarted.progress("nova",player).xp);
  }
  @Test void interruptedMultiFileCommitReplaysBeforeReading()throws Exception{
    AtomicBoolean fail=new AtomicBoolean(false);
    var io=new AtomicFiles.IO(){public void replace(Path source,Path target)throws IOException{
      if(fail.get()&&target.equals(root.resolve("data/players.json")))throw new IOException("injected interruption");
      AtomicFiles.IO.super.replace(source,target);
    }};
    var store=Store.directory(root,io);State next=store.load();next.seasons.get(definition.id).rewards.get("free:1").choices.getFirst().count=15;
    fail.set(true);assertThrows(IOException.class,()->store.save(next));assertTrue(Files.exists(root.resolve("data/commit.json")));
    State recovered=Store.directory(root).load();assertEquals(15,recovered.seasons.get(definition.id).rewards.get("free:1").choices.getFirst().count);
    assertFalse(Files.exists(root.resolve("data/commit.json")));
  }
  @Test void externalEditAfterInterruptedCommitIsPreservedForRepair()throws Exception{
    AtomicBoolean fail=new AtomicBoolean(false);
    var io=new AtomicFiles.IO(){public void replace(Path source,Path target)throws IOException{
      if(fail.get()&&target.equals(root.resolve("data/players.json")))throw new IOException("interrupted");
      AtomicFiles.IO.super.replace(source,target);
    }};
    var store=Store.directory(root,io);State next=store.load();next.seasons.get(definition.id).rewards.get("free:1").choices.getFirst().count=15;
    fail.set(true);assertThrows(IOException.class,()->store.save(next));
    Path file=root.resolve("rewards/"+definition.id+".json");var edited=Store.JSON.fromJson(Files.readString(file),RewardBook.class);edited.free.get("1").choices.getFirst().count=9;
    String manual=Store.JSON.toJson(edited.document());Files.writeString(file,manual);
    assertThrows(IOException.class,()->Store.directory(root).load());assertEquals(manual,Files.readString(file));assertTrue(Files.exists(root.resolve("data/commit.json")));
  }
  @Test void controlFileRejectsTyposAndDoesNotOverwriteInvalidInput()throws Exception{
    for(String invalid:List.of("{\"schema\":1,\"duratonDays\":10}","{\"schema\":1,\"autoStart\":\"true\"}","{\"schema\":1,\"durationDays\":\"10\"}")){
      Files.writeString(root.resolve("passe.json"),invalid);assertThrows(IOException.class,()->PassSetup.definition(root));assertEquals(invalid,Files.readString(root.resolve("passe.json")));
    }
  }
  @Test void corruptBookAndInvalidControlDoNotModifyRuntime()throws Exception{
    long end=service.active().end;definition.durationDays=0;assertThrows(IllegalArgumentException.class,()->service.configure(definition,book));assertEquals(end,service.active().end);
    Files.writeString(root.resolve("rewards/"+definition.id+".json"),"broken");assertThrows(IOException.class,()->Store.directory(root).load());
  }
  @Test void suppliedTableHasTwoCompleteTracksAndUnresolvedRewardsAreDisabled(){
    assertEquals(200,book.rewards().size());assertTrue(book.free.values().stream().allMatch(r->r.enabled));
    assertFalse(book.premium.get("100").enabled);assertFalse(book.premium.get("85").enabled);
    assertEquals("inicial_rara",book.free.get("8").choices.getFirst().value);
    assertEquals("eco deposit 100000 impactor:dollars {player}",book.free.get("100").choices.stream().filter(c->c.value.startsWith("eco deposit")&&c.value.contains("dollars")).findFirst().orElseThrow().value);
    assertTrue(book.rewards().values().stream().filter(r->!r.enabled).allMatch(r->!r.setupNote.isBlank()));
  }
}
