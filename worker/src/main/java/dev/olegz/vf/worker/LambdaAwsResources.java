package dev.olegz.vf.worker;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.ecr.EcrSupport;
import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.common.util.CollectionOps;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.lambda.model.VpcConfig;

/**
 * Lambda-deployment-specific AWS resource resolution: the VPC/subnet/security-group config for
 * lambda Lambda functions and the per-lambda ECR repository. Generic AWS access (IAM, SQS, ECR
 * client, client factories) lives in the {@code dev.olegz.vf.aws} module.
 */
public class LambdaAwsResources {

    // Map AWS entity names to their internal identifiers (VPC/subnet/security-group/repository)
    private static final ConcurrentHashMap<String, String> staticRefs = new ConcurrentHashMap<>(3);

    private static boolean matchKey(String key, String objectId, String objectName, List<Tag> objectTags) {
        return key.equals(objectId) || key.equals(objectName) || CollectionOps.anyMatch(objectTags, t -> "Name".equals(t.key()) && key.equals(t.value()));
    }

    public static VpcConfig getVpcConfig() {
        String vpcKey = PropertyStore.getString("vf.aws.lambda.vpc.name");
        if (vpcKey == null || vpcKey.isBlank()) {
            throw new ApplicationFailureException("VPC name is not set in the system properties");
        }

        String sgKey = PropertyStore.getString("vf.aws.lambda.vpc.securityGroup");
        if (sgKey == null || sgKey.isBlank()) {
            throw new ApplicationFailureException("Security group for lambdas is not set in the system properties");
        }

        try (var ec2Client = AwsClients.ec2Client()) {

            // Check VPC
            String vpcId = staticRefs.computeIfAbsent("vpc:" + vpcKey, k -> {
                Vpc vpc = CollectionOps.findAny(ec2Client.describeVpcs().vpcs(), v -> matchKey(vpcKey, v.vpcId(), null, v.tags()));
                if (vpc == null) throw new ApplicationFailureException("VPC " + vpcKey + " does not exist");
                return vpc.vpcId();
            });

            Filter vpcFilter = Filter.builder().name("vpc-id").values(vpcId).build();

            // Check security group
            String securityGroupId = staticRefs.computeIfAbsent("secgrp:" + sgKey, k -> {
                List<SecurityGroup> securityGroups = ec2Client.describeSecurityGroups(req -> req.filters(vpcFilter)).securityGroups();
                // Search security group by key: group ID, group name, or tag:Name
                SecurityGroup securityGroup = CollectionOps.findAny(securityGroups, sg -> matchKey(sgKey, sg.groupId(), sg.groupName(), sg.tags()));
                if (securityGroup == null) {
                    throw new ApplicationFailureException("Security group not found, vpc=" + vpcKey + ", group=" + sgKey);
                }
                return securityGroup.groupId();
            });

            // Check subnets
            Collection<String> subnetIds;
            List<String> subnetNames = PropertyStore.getList("vf.aws.lambda.vpc.subnets");
            if (subnetNames == null || subnetNames.isEmpty()) {
                subnetIds = CollectionOps.map(ec2Client.describeSubnets(req -> req.filters(vpcFilter)).subnets(), Subnet::subnetId);
            } else {
                subnetIds = new HashSet<>(subnetNames.size());
                for (String subnetName : subnetNames) {
                    String subnetId = staticRefs.computeIfAbsent("subnet:" + subnetName, k -> {
                        List<Subnet> subnets = ec2Client.describeSubnets(req -> req.filters(vpcFilter)).subnets();
                        Subnet subnet = CollectionOps.findAny(subnets, s -> matchKey(subnetName, s.subnetId(), s.subnetArn(), s.tags()));
                        if (subnet == null) {
                            throw new ApplicationFailureException("Subnet not found, vpc=" + vpcKey + ", subnet=" + subnetName);
                        }
                        return subnet.subnetId();
                    });
                    subnetIds.add(subnetId);
                }
            }
            if ((subnetIds == null) || subnetIds.isEmpty()) {
                throw new ApplicationFailureException("Not found subnets for specified VPC");
            }

            return VpcConfig.builder().securityGroupIds(securityGroupId).subnetIds(subnetIds).build();
        }
    }


    // get or create a repository in the ECR private registry (cached per lambda)
    public static String makeEcrRepositoryUri(String repoName) {
        return staticRefs.computeIfAbsent("ecr:" + repoName, k -> EcrSupport.getOrCreateRepository(repoName));
    }

}
