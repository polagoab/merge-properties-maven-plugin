#!/bin/sh
# Copyright 2015 Polago AB.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# Create a release of this project
#

project="merge-properties-maven-plugin"
cur_version=`mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '\[' | grep -v 'Download'`

if [ $# -ne 1 ]; then
	echo "Usage: mkrelease.sh version"
	echo "Current $project version is $cur_version."
	exit 1
fi

branch=`git rev-parse --abbrev-ref HEAD`
if [ "master" != "$branch" ]; then
    echo "Not on branch master: $branch"
    exit 1
fi

set -e

version=$1
tag=v$version

echo "Creating a new release of project '$project' with version: $version."
echo "Current $project version is $cur_version."
echo

#
# Update workspace from origin
#
echo "Updating local workspace from origin..."
git pull

echo "Verifying that everything is checked in..."
#
# git status --porcelain
#
stat=`git status --porcelain`

if [ ! -z "$stat" ]; then
    echo "The workspace is not clean"
    echo "$stat"
    exit 1
fi

#
# Make sure that all tests pass
#
echo "Running tests..."
mvn clean test

echo ""
read -p "Do you want to create the release[yn]" answer

if [ "$answer" != "y" ]; then
	echo "bailing out!"
	exit 1
fi

#
# Update version number in pom.xml
#
echo "Updating pom.xml with new version: $version"
mvn versions:set -DnewVersion=$version -DgenerateBackupPoms=false

#
# Commit pom.xml
#
echo "Committing pom.xml..."
git commit -m "$project version $version" pom.xml

#
# Creating tag in Git
#
echo "Creating annotated tag: $project-$version" 
git tag -m "$project version $version" -a $tag

#
# Publish to origin
#
echo "Pushing to origin..." 
git push

#
# Publishing Git tag to origin
#
echo "Publishing tag to origin: $tag"
git push origin $tag

#
# Deploying artifact to ossrh
#
mvn -Possrh clean deploy

#
# Publishing site
#
mvn site site:deploy

echo "Please create a GitHub release info"
