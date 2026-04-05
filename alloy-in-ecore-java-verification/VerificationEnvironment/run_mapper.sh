#!/bin/bash
mvn exec:java -Dexec.mainClass="com.verification.mapper.JsonToAieMapper" -Dexec.args="my_output.json src/main/resources/TargetInstance.aie"
