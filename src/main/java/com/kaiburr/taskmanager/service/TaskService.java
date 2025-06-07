package com.kaiburr.taskmanager.service;

import com.kaiburr.taskmanager.model.Task;
import com.kaiburr.taskmanager.model.TaskExecution;
import com.kaiburr.taskmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Optional<Task> getTaskById(String id) {
        return taskRepository.findById(id);
    }

    public List<Task> findTasksByName(String name) {
        return taskRepository.findByNameContainingIgnoreCase(name);
    }

    public Task createOrUpdateTask(Task task) {
        return taskRepository.save(task);
    }

    public void deleteTask(String id) {
        taskRepository.deleteById(id);
    }

    public Task executeTaskCommand(String taskId) throws Exception {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new Exception("Task not found"));

        String command = task.getCommand();

        // Safety check
        if (command.contains("rm") || command.contains("shutdown") || command.contains("format")) {
            throw new Exception("Unsafe command detected");
        }

        try {
            // Connect to Kubernetes API
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);
            CoreV1Api api = new CoreV1Api();

            String podName = "runner-" + System.currentTimeMillis();

            // Define pod that runs the shell command
            V1Pod pod = new V1Pod()
                    .metadata(new V1ObjectMeta().name(podName))
                    .spec(new V1PodSpec()
                            .restartPolicy("Never")
                            .containers(List.of(new V1Container()
                                    .name("runner")
                                    .image("busybox")
                                    .command(List.of("sh", "-c", command)))));

            // Create pod
            api.createNamespacedPod("default", pod, null, null, null);

            // Wait until pod is completed
            while (true) {
                V1Pod currentPod = api.readNamespacedPod(podName, "default", null);
                String phase = currentPod.getStatus().getPhase();
                if ("Succeeded".equals(phase) || "Failed".equals(phase)) {
                    break;
                }
                Thread.sleep(1000);
            }

            // Read pod logs (output of command)
            String logs = api.readNamespacedPodLog(podName, "default", null,
                    null, null, null, null, null, null, null);

            // Save to taskExecutions
            TaskExecution exec = new TaskExecution();
            exec.setStartTime(LocalDateTime.now());
            exec.setEndTime(LocalDateTime.now());
            exec.setOutput(logs);

            task.getTaskExecutions().add(exec);
            return taskRepository.save(task);

        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Failed to run task in Kubernetes: " + e.getMessage());
        }
    }
}
