package com.example.terradiver.kinetics;

import com.example.terradiver.registry.BlockEntityRegistry;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/*
 * Буровой подшипник. Наследуемся от механического подшипника Create — оттуда бесплатно приезжают:
 *  - ПКМ пустой рукой: собрать/разобрать контраптию перед лицом (корону);
 *  - вращается ТОЛЬКО когда собрано (без короны собирать нечего → не крутится вхолостую);
 *  - ключ (wrench) разбирает; вращение собранной короны перед неподвижным корпусом (как у Aeronautics).
 * FACING, ось вращения, приём вала с тыла, ПКМ-обработчик — уже в родителе, не трогаем.
 * Отличия (требование короны + нагрузка по её размеру) — в CrownBearingBlockEntity.
 *
 * IBE<...> заново НЕ объявляем: родитель уже IBE<MechanicalBearingBlockEntity>, а тот же generic-
 * интерфейс дважды с разными параметрами Java не разрешает. Достаточно переопределить тип BE —
 * wildcard "? extends MechanicalBearingBlockEntity" принимает наш подтип. getBlockEntityClass()
 * наследуем (вернёт MechanicalBearingBlockEntity.class; наш BE — его подкласс, проверки проходят).
 */
public class CrownBearingBlock extends MechanicalBearingBlock {

    public CrownBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends MechanicalBearingBlockEntity> getBlockEntityType() {
        return BlockEntityRegistry.CROWN_BEARING.get();
    }
}
