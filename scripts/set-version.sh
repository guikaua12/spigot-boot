#!/bin/bash
./mvnw versions:set -DnewVersion=$1 -DprocessAllModules -DgenerateBackupPoms=false