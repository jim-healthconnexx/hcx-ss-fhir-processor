package com.hcx.fhir.processor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// HDC-233: ECS task trigger config — used to launch hcx-ss-fhir-import after FHIR processing completes.
@ConfigurationProperties(prefix = "aws.ecs")
@Data
public class EcsProperties {

    /** ARN or short name of the ECS cluster to run the import task on. */
    private String clusterArn;

    /** Family name (or ARN) of the hcx-ss-fhir-import task definition.
     *  Use the family name (e.g. "hcx-ss-fhir-import") to always run the latest active revision. */
    private String taskDefinitionArn;

    /** VPC subnet IDs for the Fargate task's awsvpc network configuration. */
    private List<String> subnets;

    /** Security group IDs to attach to the Fargate task. */
    private List<String> securityGroups;

    /** Whether to assign a public IP to the Fargate task. Defaults to DISABLED. */
    private String assignPublicIp = "DISABLED";

    /** HDC-233: When false, skip the ECS trigger (useful in local/dev profiles). Defaults to true. */
    private boolean enabled = true;
}
