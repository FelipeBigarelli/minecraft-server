package codes.biga.bigacore;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import java.util.Locale;

/**
 * Gera a loja física da economia em posição relativa ao spawn real do mundo.
 *
 * A construção nunca roda automaticamente. O admin primeiro inspeciona o plano
 * com /biga loja validar e só depois executa /biga loja criar CONFIRMAR.
 *
 * O mundo não fica no Git, então coordenadas absolutas seriam frágeis. Em vez
 * disso, a loja usa o spawn retornado pelo próprio Bukkit em runtime e um offset
 * configurável no config.yml. Assim a mesma versão do BigaCore funciona após
 * restaurar o mundo em outra máquina.
 */
public final class SpawnShopBuilder {

    private static final int HALF_WIDTH = 10;
    private static final int MIN_V = -2;
    private static final int MAX_V = 14;
    private static final int BUILD_HEIGHT = 12;

    private final BigaCore plugin;

    public SpawnShopBuilder(BigaCore plugin) {
        this.plugin = plugin;
    }

    public Analysis analyze(World world) {
        Location spawn = world.getSpawnLocation();

        if (world.getEnvironment() != World.Environment.NORMAL) {
            return Analysis.invalid(
                    spawn,
                    "A loja do spawn só pode ser planejada no Overworld."
            );
        }

        int offsetX = plugin.getConfig().getInt("loja-spawn.offset-x", 28);
        int offsetZ = plugin.getConfig().getInt("loja-spawn.offset-z", 0);
        int maxSlope = plugin.getConfig().getInt("loja-spawn.max-desnivel", 3);

        if (offsetX == 0 && offsetZ == 0) {
            return Analysis.invalid(
                    spawn,
                    "O offset da loja não pode ser 0,0 porque isso pisaria no spawn."
            );
        }

        int centerX = spawn.getBlockX() + offsetX;
        int centerZ = spawn.getBlockZ() + offsetZ;
        BlockFace frontFace = frontFace(offsetX, offsetZ);
        Layout layout = new Layout(centerX, centerZ, frontFace);

        int minSurfaceY = world.getMaxHeight();
        int maxSurfaceY = world.getMinHeight();
        int suspiciousSurfaceBlocks = 0;

        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                Block block = layout.block(world, u, v, 0);
                int surfaceY = world.getHighestBlockYAt(block.getX(), block.getZ());
                minSurfaceY = Math.min(minSurfaceY, surfaceY);
                maxSurfaceY = Math.max(maxSurfaceY, surfaceY);

                Material surface = world.getBlockAt(block.getX(), surfaceY, block.getZ()).getType();
                if (!isExpectedSurface(surface)) {
                    suspiciousSurfaceBlocks++;
                }
            }
        }

        int slope = maxSurfaceY - minSurfaceY;
        int floorY = maxSurfaceY;
        int obstructionCount = countObstructions(world, layout, floorY);

        boolean safe = true;
        String reason = "Área compatível com a construção.";

        if (slope > maxSlope) {
            safe = false;
            reason = "Desnível de " + slope + " blocos excede o máximo de " + maxSlope + ".";
        } else if (obstructionCount > 0) {
            safe = false;
            reason = "Há " + obstructionCount + " bloco(s) sólido(s), líquido(s) ou bloco(s) com dados na área aérea.";
        } else if (suspiciousSurfaceBlocks > 12) {
            safe = false;
            reason = "A superfície tem muitos blocos que parecem construção existente ("
                    + suspiciousSurfaceBlocks + ").";
        }

        Location center = new Location(world, centerX + 0.5, floorY + 1.0, centerZ + 0.5);
        return new Analysis(
                true,
                safe,
                reason,
                spawn.clone(),
                center,
                frontFace,
                floorY,
                minSurfaceY,
                maxSurfaceY,
                suspiciousSurfaceBlocks,
                obstructionCount
        );
    }

    public BuildResult build(World world) {
        Analysis analysis = analyze(world);
        if (!analysis.valid()) {
            return new BuildResult(false, analysis.reason(), analysis);
        }
        if (!analysis.safe()) {
            return new BuildResult(false, "Construção bloqueada pela validação: " + analysis.reason(), analysis);
        }

        Layout layout = new Layout(
                analysis.center().getBlockX(),
                analysis.center().getBlockZ(),
                analysis.frontFace()
        );

        prepareFoundation(world, layout, analysis.floorY());
        clearReplaceableVolume(world, layout, analysis.floorY());
        buildFloor(world, layout, analysis.floorY());
        buildMainStructure(world, layout, analysis.floorY());
        buildRoof(world, layout, analysis.floorY());
        buildMarketAwning(world, layout, analysis.floorY());
        buildTradeCorner(world, layout, analysis.floorY());
        buildDecor(world, layout, analysis.floorY());
        placeMarketSign(world, layout, analysis.floorY());

        return new BuildResult(
                true,
                "Biga Market construída com referência no spawn real do mundo.",
                analysis
        );
    }

    private int countObstructions(World world, Layout layout, int floorY) {
        int count = 0;

        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                Block column = layout.block(world, u, v, 0);
                int surfaceY = world.getHighestBlockYAt(column.getX(), column.getZ());

                for (int y = surfaceY + 1; y <= floorY + BUILD_HEIGHT; y++) {
                    Block block = world.getBlockAt(column.getX(), y, column.getZ());
                    if (isAirOrReplaceable(block)) {
                        continue;
                    }
                    count++;
                }
            }
        }

        return count;
    }

    private boolean isAirOrReplaceable(Block block) {
        Material material = block.getType();
        if (material.isAir()) {
            return true;
        }
        if (block.isLiquid()) {
            return false;
        }
        if (block.getState() instanceof TileState) {
            return false;
        }
        return !material.isSolid();
    }

    private boolean isExpectedSurface(Material material) {
        return switch (material) {
            case GRASS_BLOCK,
                 DIRT,
                 COARSE_DIRT,
                 ROOTED_DIRT,
                 PODZOL,
                 MYCELIUM,
                 MUD,
                 MUDDY_MANGROVE_ROOTS,
                 SAND,
                 RED_SAND,
                 GRAVEL,
                 STONE,
                 DEEPSLATE,
                 ANDESITE,
                 DIORITE,
                 GRANITE,
                 TUFF,
                 CALCITE,
                 DIRT_PATH,
                 COBBLESTONE,
                 MOSSY_COBBLESTONE,
                 STONE_BRICKS,
                 MOSSY_STONE_BRICKS,
                 POLISHED_ANDESITE -> true;
            default -> false;
        };
    }

    private void prepareFoundation(World world, Layout layout, int floorY) {
        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                Block column = layout.block(world, u, v, 0);
                int surfaceY = world.getHighestBlockYAt(column.getX(), column.getZ());

                for (int y = surfaceY + 1; y <= floorY; y++) {
                    Material material = Material.COBBLESTONE;
                    if (y == floorY) {
                        material = Material.STONE_BRICKS;
                    }
                    world.getBlockAt(column.getX(), y, column.getZ()).setType(material, false);
                }
            }
        }
    }

    private void clearReplaceableVolume(World world, Layout layout, int floorY) {
        for (int u = -8; u <= 8; u++) {
            for (int v = 2; v <= 12; v++) {
                for (int y = 1; y <= 10; y++) {
                    Block block = layout.block(world, u, v, floorY + y);
                    if (isAirOrReplaceable(block)) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void buildFloor(World world, Layout layout, int floorY) {
        for (int u = -HALF_WIDTH; u <= HALF_WIDTH; u++) {
            for (int v = MIN_V; v <= MAX_V; v++) {
                Material floorMaterial = Material.STONE_BRICKS;

                boolean border = Math.abs(u) == HALF_WIDTH || v == MIN_V || v == MAX_V;
                if (border) {
                    floorMaterial = Material.POLISHED_ANDESITE;
                } else if ((Math.abs(u) + Math.abs(v)) % 7 == 0) {
                    floorMaterial = Material.ANDESITE;
                }

                layout.block(world, u, v, floorY).setType(floorMaterial, false);
            }
        }
    }

    private void buildMainStructure(World world, Layout layout, int floorY) {
        for (int y = 1; y <= 5; y++) {
            for (int u = -8; u <= 8; u++) {
                placeWallBlock(world, layout, u, 2, floorY + y, y, true);
                placeWallBlock(world, layout, u, 12, floorY + y, y, false);
            }

            for (int v = 3; v <= 11; v++) {
                placeSideWallBlock(world, layout, -8, v, floorY + y, y);
                placeSideWallBlock(world, layout, 8, v, floorY + y, y);
            }
        }

        for (int v = 2; v <= 12; v++) {
            for (int u : new int[]{-8, 8}) {
                for (int y = 1; y <= 6; y++) {
                    layout.block(world, u, v, floorY + y)
                            .setType(Material.STRIPPED_DARK_OAK_LOG, false);
                }
            }
        }

        for (int u : new int[]{-4, 4}) {
            for (int y = 1; y <= 5; y++) {
                layout.block(world, u, 2, floorY + y)
                        .setType(Material.STRIPPED_DARK_OAK_LOG, false);
            }
        }

        // Entrada ampla — mantém a circulação boa mesmo com vários jogadores.
        for (int u = -2; u <= 2; u++) {
            for (int y = 1; y <= 4; y++) {
                layout.block(world, u, 2, floorY + y).setType(Material.AIR, false);
            }
        }

        // Janelas frontais e laterais.
        for (int u : new int[]{-6, -5, 5, 6}) {
            for (int y = 2; y <= 3; y++) {
                layout.block(world, u, 2, floorY + y).setType(Material.GLASS_PANE, false);
            }
        }
        for (int v : new int[]{5, 6, 8, 9}) {
            for (int y = 2; y <= 3; y++) {
                layout.block(world, -8, v, floorY + y).setType(Material.GLASS_PANE, false);
                layout.block(world, 8, v, floorY + y).setType(Material.GLASS_PANE, false);
            }
        }
    }

    private void placeWallBlock(World world, Layout layout, int u, int v,
                                int worldY, int relativeY, boolean front) {
        if (front && u >= -2 && u <= 2 && relativeY <= 4) {
            return;
        }

        Material material = Material.SPRUCE_PLANKS;
        if (relativeY == 1) {
            material = Material.STONE_BRICKS;
        }
        layout.block(world, u, v, worldY).setType(material, false);
    }

    private void placeSideWallBlock(World world, Layout layout, int u, int v,
                                    int worldY, int relativeY) {
        Material material = Material.SPRUCE_PLANKS;
        if (relativeY == 1) {
            material = Material.STONE_BRICKS;
        }
        layout.block(world, u, v, worldY).setType(material, false);
    }

    private void buildRoof(World world, Layout layout, int floorY) {
        // Telhado escalonado: aparência medieval sem depender de WorldEdit.
        for (int u = -9; u <= 9; u++) {
            int distanceFromEdge = 9 - Math.abs(u);
            int roofY = floorY + 6 + (distanceFromEdge / 2);

            for (int v = 1; v <= 13; v++) {
                Material material = Material.SPRUCE_PLANKS;
                if ((v + Math.abs(u)) % 5 == 0) {
                    material = Material.DARK_OAK_PLANKS;
                }
                layout.block(world, u, v, roofY).setType(material, false);
            }
        }

        // Cumeeira.
        for (int v = 1; v <= 13; v++) {
            layout.block(world, 0, v, floorY + 10).setType(Material.DARK_OAK_SLAB, false);
        }

        // Gable frontal em madeira escura.
        for (int y = 6; y <= 9; y++) {
            int half = 9 - ((y - 6) * 2);
            if (half < 1) {
                half = 1;
            }
            for (int u = -half; u <= half; u++) {
                layout.block(world, u, 2, floorY + y).setType(Material.DARK_OAK_PLANKS, false);
            }
        }

        // Pequena chaminé lateral.
        for (int y = 7; y <= 10; y++) {
            layout.block(world, 6, 10, floorY + y).setType(Material.STONE_BRICKS, false);
        }
        layout.block(world, 6, 10, floorY + 11).setType(Material.CAMPFIRE, false);
    }

    private void buildMarketAwning(World world, Layout layout, int floorY) {
        for (int u = -8; u <= -4; u++) {
            for (int v = 0; v <= 1; v++) {
                Material canopy = Material.BLUE_WOOL;
                if (Math.abs(u) % 2 == 0) {
                    canopy = Material.WHITE_WOOL;
                }
                layout.block(world, u, v, floorY + 4).setType(canopy, false);
            }
        }

        for (int u : new int[]{-8, -4}) {
            for (int y = 1; y <= 3; y++) {
                layout.block(world, u, 0, floorY + y).setType(Material.OAK_FENCE, false);
            }
        }

        layout.block(world, -7, 1, floorY + 1).setType(Material.BARREL, false);
        layout.block(world, -5, 1, floorY + 1).setType(Material.BARREL, false);
    }

    private void buildTradeCorner(World world, Layout layout, int floorY) {
        // Chests vazios: os jogadores transformam estes pontos em ChestShops
        // com seus próprios preços. O BigaCore não cria Admin Shops infinitas.
        for (int u = 4; u <= 7; u++) {
            layout.block(world, u, 4, floorY + 1).setType(Material.CHEST, false);
            layout.block(world, u, 5, floorY + 1).setType(Material.BARREL, false);
            layout.block(world, u, 4, floorY + 2).setType(Material.OAK_SLAB, false);
        }

        for (int u = 3; u <= 7; u++) {
            layout.block(world, u, 6, floorY + 1).setType(Material.DARK_OAK_SLAB, false);
        }
    }

    private void buildDecor(World world, Layout layout, int floorY) {
        for (int u : new int[]{-7, 7}) {
            layout.block(world, u, 1, floorY + 1).setType(Material.BARREL, false);
            layout.block(world, u, 1, floorY + 2).setType(Material.FLOWERING_AZALEA_LEAVES, false);
        }

        // Material.CHAIN não existe na API 26.2 usada pelo projeto; IRON_BARS
        // mantém o detalhe vertical da luminária sem depender de material legado.
        for (int u : new int[]{-6, 6}) {
            layout.block(world, u, 1, floorY + 5).setType(Material.IRON_BARS, false);
            layout.block(world, u, 1, floorY + 4).setType(Material.LANTERN, false);
        }

        // Iluminação interna discreta.
        for (int u : new int[]{-5, 0, 5}) {
            layout.block(world, u, 8, floorY + 5).setType(Material.LANTERN, false);
        }
    }

    private void placeMarketSign(World world, Layout layout, int floorY) {
        Block signBlock = layout.block(world, 0, 1, floorY + 5);
        signBlock.setType(Material.OAK_WALL_SIGN, false);

        BlockData data = signBlock.getBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(layout.frontFace());
            signBlock.setBlockData(directional, false);
        }

        if (signBlock.getState() instanceof Sign sign) {
            sign.line(0, Component.text("BIGA"));
            sign.line(1, Component.text("MARKET"));
            sign.line(2, Component.text("B$"));
            sign.line(3, Component.text("ChestShops"));
            sign.update(true, false);
        }
    }

    private BlockFace frontFace(int offsetX, int offsetZ) {
        if (Math.abs(offsetX) >= Math.abs(offsetZ)) {
            if (offsetX > 0) {
                return BlockFace.WEST;
            }
            return BlockFace.EAST;
        }

        if (offsetZ > 0) {
            return BlockFace.NORTH;
        }
        return BlockFace.SOUTH;
    }

    public record Analysis(
            boolean valid,
            boolean safe,
            String reason,
            Location spawn,
            Location center,
            BlockFace frontFace,
            int floorY,
            int minSurfaceY,
            int maxSurfaceY,
            int suspiciousSurfaceBlocks,
            int obstructionCount
    ) {
        private static Analysis invalid(Location spawn, String reason) {
            return new Analysis(
                    false,
                    false,
                    reason,
                    spawn,
                    spawn.clone(),
                    BlockFace.NORTH,
                    spawn.getBlockY(),
                    spawn.getBlockY(),
                    spawn.getBlockY(),
                    0,
                    0
            );
        }

        public String facingName() {
            return frontFace.name().toLowerCase(Locale.ROOT);
        }
    }

    public record BuildResult(boolean success, String message, Analysis analysis) {
    }

    private record Layout(int centerX, int centerZ, BlockFace frontFace) {

        private Block block(World world, int u, int v, int y) {
            BlockFace inward = frontFace.getOppositeFace();
            BlockFace right = rightOf(inward);

            int frontCenterX = centerX - inward.getModX() * 7;
            int frontCenterZ = centerZ - inward.getModZ() * 7;

            int x = frontCenterX + inward.getModX() * v + right.getModX() * u;
            int z = frontCenterZ + inward.getModZ() * v + right.getModZ() * u;
            return world.getBlockAt(x, y, z);
        }

        private static BlockFace rightOf(BlockFace inward) {
            return switch (inward) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                case WEST -> BlockFace.NORTH;
                default -> throw new IllegalArgumentException("Direção não cardinal: " + inward);
            };
        }
    }
}
