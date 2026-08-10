package com.hcx.fhir.processor.service;

import com.hcx.fhir.processor.config.EcsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AwsVpcConfiguration;
import software.amazon.awssdk.services.ecs.model.LaunchType;
import software.amazon.awssdk.services.ecs.model.NetworkConfiguration;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.Task;

import java.util.List;

// HDC-233: Triggers the hcx-ss-fhir-import ECS task after all FHIR processing is complete.
@Slf4j
@Service
@RequiredArgsConstructor
public class EcsTaskService {

    private final EcsClient ecsClient;
    private final EcsProperties ecsProperties;

    /**
     * HDC-233: Launches the hcx-ss-fhir-import ECS Fargate task.
     * Logs the resulting task ARN on success; throws on failure.
     */
    public void runImportTask() {
        if (!ecsProperties.isEnabled()) {
            log.info("HDC-233: ECS task trigger is disabled via aws.ecs.enabled=false — skipping");
            return;
        }

        log.info("HDC-233: Triggering ECS task taskDefinition={} on cluster={}",
                ecsProperties.getTaskDefinitionArn(), ecsProperties.getClusterArn());

        AwsVpcConfiguration vpcConfig = AwsVpcConfiguration.builder()
                .subnets(ecsProperties.getSubnets())
                .securityGroups(ecsProperties.getSecurityGroups())
                .assignPublicIp(ecsProperties.getAssignPublicIp())
                .build();

        NetworkConfiguration networkConfig = NetworkConfiguration.builder()
                .awsvpcConfiguration(vpcConfig)
                .build();

        RunTaskRequest request = RunTaskRequest.builder()
                .cluster(ecsProperties.getClusterArn())
                .taskDefinition(ecsProperties.getTaskDefinitionArn())
                .launchType(LaunchType.FARGATE)
                .networkConfiguration(networkConfig)
                .build();

        try {
            RunTaskResponse response = ecsClient.runTask(request);

            List<Task> tasks = response.tasks();
            if (tasks.isEmpty()) {
                String failures = response.failures().toString();
                log.error("HDC-233: ECS RunTask returned no tasks — failures={}", failures);
                throw new IllegalStateException("HDC-233: ECS RunTask returned no tasks: " + failures);
            }

            tasks.forEach(task ->
                    log.info("HDC-233: ECS import task launched taskArn={} lastStatus={}",
                            task.taskArn(), task.lastStatus()));

        } catch (Exception e) {
            log.error("HDC-233: Failed to launch ECS import task", e);
            throw e;
        }
    }
}
