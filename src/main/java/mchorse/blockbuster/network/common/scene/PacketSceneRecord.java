package mchorse.blockbuster.network.common.scene;

import io.netty.buffer.ByteBuf;
import mchorse.blockbuster.recording.scene.SceneLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public class PacketSceneRecord extends PacketScene
{
    public String record = "";
    public int offset;

    public PacketSceneRecord()
    {}

    public PacketSceneRecord(SceneLocation location, String record, int offset)
    {
        this(location, record);

        this.offset = offset;
    }

    public PacketSceneRecord(SceneLocation location, String record)
    {
        super(location);

        this.record = record;
    }

    @Override
    public void fromBytes(ByteBuf buf)
    {
        super.fromBytes(buf);

        this.record = ByteBufUtils.readUTF8String(buf);
        this.offset = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf)
    {
        super.toBytes(buf);

        ByteBufUtils.writeUTF8String(buf, this.record);
        buf.writeInt(this.offset);
    }
}