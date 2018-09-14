package com.github.bihealth.varhab;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.github.bihealth.varhab.annotate.AnnotateArgs;
import com.github.bihealth.varhab.annotate.AnnotateVcf;
import com.github.bihealth.varhab.init_db.InitDb;
import com.github.bihealth.varhab.init_db.InitDbArgs;

public class VarhabCli {

  public static void main(String[] args) {
    final InitDbArgs initDb = new InitDbArgs();
    final AnnotateArgs annotate = new AnnotateArgs();

    final JCommander jc =
        JCommander.newBuilder()
            .addCommand("init-db", initDb)
            .addCommand("annotate", annotate)
            .build();

    if ((args == null || args.length == 0)) {
      jc.usage();
      System.exit(1);
    }

    try {
      jc.parse(args);
    } catch (MissingCommandException e) {
      System.exit(1);
      return;
    }

    final String cmd = jc.getParsedCommand();
    if (cmd == null) {
      jc.usage();
      System.exit(1);
    }

    switch (cmd) {
      case "init-db":
        if (initDb.isHelp()) {
          jc.usage("init-db");
        } else {
          new InitDb(initDb).run();
        }
        break;
      case "annotate":
        System.err.println("annotate: " + annotate);
        if (annotate.isHelp()) {
          jc.usage("annotate");
        } else {
          new AnnotateVcf(annotate).run();
        }
        break;
      default:
        System.err.println("Unknown command: " + cmd);
        System.exit(1);
    }
  }
}
