package jp.apple.aris;

import jp.apple.aris.ctc.command.CommandCtc;
import jp.apple.aris.ctc.config.LineManager;
import jp.apple.aris.ctc.state.LineStateManager;
import jp.apple.aris.util.ArisDir;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

// ARIS : Automatic Route Integrated System
@Mod(modid = Tags.MOD_ID,name = Tags.MOD_NAME,version = Tags.VERSION)
public class ArisCore {
    public static final String ID = Tags.MOD_ID;
    public static final String NAME = Tags.MOD_NAME;
    public static final String VERSION = Tags.VERSION;
            
    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Loaded: {}",Tags.MOD_NAME);

        Object touch = ArisItem.RAIL_REGISTER_TOOL;
        
        File currentModFile = event.getSourceFile();
        File modsDirectory = currentModFile.getParentFile();
        ArisDir.init(modsDirectory);
    }
    
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LineManager.loadAllLines();
        LineStateManager.initializeStates();
        event.registerServerCommand(new CommandCtc());
    }
}
