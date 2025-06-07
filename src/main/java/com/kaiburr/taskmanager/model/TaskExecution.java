package com.kaiburr.taskmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecution {
    private Date startTime;
    private Date endTime;
    private String output;
}
