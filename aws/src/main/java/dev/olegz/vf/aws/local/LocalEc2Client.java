package dev.olegz.vf.aws.local;

import java.util.ArrayList;
import java.util.List;

import dev.olegz.vf.common.props.PropertyStore;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

/**
 * Local synthetic implementation of {@link Ec2Client}. It echoes back the VPC / security-group /
 * subnet identifiers configured under {@code vf.aws.lambda.vpc.*} so that
 * {@code dev.olegz.vf.worker.LambdaAwsResources#getVpcConfig()} resolves without a
 * real VPC. (Only exercised once lambda Lambda deployment runs locally - a later phase.)
 */
public final class LocalEc2Client implements Ec2Client {

    @Override
    public DescribeVpcsResponse describeVpcs(DescribeVpcsRequest request) {
        String vpcId = PropertyStore.getString("vf.aws.lambda.vpc.name", "vpc-local");
        return DescribeVpcsResponse.builder().vpcs(Vpc.builder().vpcId(vpcId).build()).build();
    }

    @Override
    public DescribeSecurityGroupsResponse describeSecurityGroups(DescribeSecurityGroupsRequest request) {
        String groupId = PropertyStore.getString("vf.aws.lambda.vpc.securityGroup", "sg-local");
        return DescribeSecurityGroupsResponse.builder()
            .securityGroups(SecurityGroup.builder().groupId(groupId).groupName(groupId).build()).build();
    }

    @Override
    public DescribeSubnetsResponse describeSubnets(DescribeSubnetsRequest request) {
        List<String> subnetNames = PropertyStore.getList("vf.aws.lambda.vpc.subnets");
        List<Subnet> subnets = new ArrayList<>();
        if ((subnetNames == null) || subnetNames.isEmpty()) {
            subnets.add(Subnet.builder().subnetId("subnet-local").build());
        } else {
            for (String name : subnetNames) {
                subnets.add(Subnet.builder().subnetId(name).subnetArn(name).build());
            }
        }
        return DescribeSubnetsResponse.builder().subnets(subnets).build();
    }

    @Override
    public String serviceName() {
        return Ec2Client.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
