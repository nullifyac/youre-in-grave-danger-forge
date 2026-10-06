package triage;

import cpw.mods.modlauncher.api.ServiceRunner;
import java.lang.reflect.InvocationTargetException;
import net.minecraftforge.fml.loading.targets.ForgeServerLaunchHandler;

/** Runs production common mod initialization without entering Minecraft server Main. */
public final class YigdTriageLaunchHandler extends ForgeServerLaunchHandler {
    @Override
    public String name() {
        // Mixin 0.8.5 derives SERVER from the launch target containing "server".
        return "yigdtriageserver";
    }

    @Override
    protected ServiceRunner makeService(String[] args, ModuleLayer layer) {
        return () -> {
            try {
                ClassLoader loader = layer.findLoader("minecraft");
                System.out.println("YIGD_TRIAGE: production common mod initialization; no hosted server");
                call(loader, "net.minecraft.SharedConstants", "m_142977_");
                call(loader, "net.minecraft.server.Bootstrap", "m_135870_");
                call(loader, "net.minecraftforge.server.loading.ServerModLoader", "load");
                System.out.println("YIGD_TRIAGE: ServerModLoader.load completed");
                System.exit(0);
            } catch (Throwable failure) {
                System.err.println("YIGD_TRIAGE: common mod initialization failed");
                failure.printStackTrace();
                System.exit(1);
            }
        };
    }

    private static void call(ClassLoader loader, String className, String method) throws Throwable {
        try {
            Class.forName(className, true, loader).getMethod(method).invoke(null);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
