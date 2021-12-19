package mchorse.blockbuster.network.server;

import mchorse.blockbuster.common.GunProps;
import mchorse.blockbuster.common.item.ItemGun;
import mchorse.blockbuster.network.Dispatcher;
import mchorse.blockbuster.network.common.guns.PacketGunInfo;
import mchorse.blockbuster.network.common.guns.PacketGunReloading;
import mchorse.blockbuster.utils.NBTUtils;
import mchorse.mclib.network.ServerMessageHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * \* User: Evanechecssss
 * \* https://evanechecssss.github.io
 * \
 */
public class ServerHandlerGunReloading  extends ServerMessageHandler<PacketGunReloading> {
    @Override
    public void run(EntityPlayerMP entityPlayerMP, PacketGunReloading packetGunReloading) {
        if (!entityPlayerMP.world.isRemote){
            ItemGun gun = (ItemGun) packetGunReloading.itemStack.getItem();
            Entity entity = entityPlayerMP.world.getEntityByID(packetGunReloading.id);
            GunProps props = NBTUtils.getGunProps(packetGunReloading.itemStack);
            if (props==null){return;}
            if (entity instanceof EntityPlayer){
                EntityPlayer player = (EntityPlayer) entity;
                if (props.getGUNState()== ItemGun.GunState.NEED_TO_BE_RELOAD) {
                    if (!player.capabilities.isCreativeMode && !props.ammoStack.isEmpty())
                    {
                        ItemStack ammo = props.ammoStack;
                        if (gun.consumeAmmoStack(player,ammo)){
                            ammo(props,player);
                        }
                    }else {
                        ammo(props,player);
                    }

                }
            }
        }
    }
    private void ammo(GunProps props, EntityPlayer entityPlayer){
        props.setGUNState(ItemGun.GunState.RELOADING);
        props.reloadTick = props.inputReloadingTime;
        Dispatcher.sendToServer(new PacketGunInfo(props.toNBT(), entityPlayer.getEntityId()));
    }
}