package dev.apexpredator.client;

import dev.apexpredator.entity.ApexEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Placeholder model; animation names are kept independent of a future GeckoLib adapter. */
public final class ApexModel extends HumanoidModel<ApexEntity> {
    public ApexModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(ApexEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        body.xRot = 0.0F;
        body.zRot = 0.0F;
        head.zRot = 0.0F;
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float pulse = Mth.sin(ageInTicks * 0.3F);
        switch (entity.getAnimationState()) {
            case RUN, CHARGE -> {
                body.xRot = 0.18F;
                rightArm.xRot = -0.55F - pulse * 0.35F;
                leftArm.xRot = -0.55F + pulse * 0.35F;
            }
            case ATTACK -> {
                rightArm.xRot = -1.7F + pulse * 0.6F;
                leftArm.xRot = -0.75F;
                body.yRot = pulse * 0.15F;
            }
            case JUMP -> {
                rightArm.xRot = -2.2F;
                leftArm.xRot = -2.2F;
                rightLeg.xRot = -0.6F;
                leftLeg.xRot = 0.35F;
            }
            case DODGE -> {
                body.xRot = 0.3F;
                head.zRot = pulse * 0.12F;
                rightArm.zRot = 0.6F;
                leftArm.zRot = -0.6F;
            }
            case TELEPORT -> {
                rightArm.xRot = -1.4F;
                leftArm.xRot = -1.4F;
                rightArm.yRot = -0.6F;
                leftArm.yRot = 0.6F;
            }
            case SPECIAL -> {
                rightArm.xRot = -2.7F;
                leftArm.xRot = -2.7F;
                rightArm.zRot = 0.45F;
                leftArm.zRot = -0.45F;
                head.xRot = -0.3F;
            }
            case HURT -> {
                body.xRot = -0.12F;
                head.xRot += 0.15F;
            }
            case DEATH -> {
                head.xRot = 0.45F;
                rightArm.xRot = -0.2F;
                leftArm.xRot = -0.2F;
            }
            default -> { }
        }
        hat.copyFrom(head);
    }
}
