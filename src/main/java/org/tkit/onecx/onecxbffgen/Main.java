package org.tkit.onecx.onecxbffgen;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.tkit.onecx.onecxbffgen.commands.CreateBffCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "onecx-bff-generator",
        mixinStandardHelpOptions = true,
        description = "OneCX BFF generator CLI",
        subcommands = { CommandLine.HelpCommand.class, CreateBffCommand.class }
)
@TopCommand
public class Main {

}