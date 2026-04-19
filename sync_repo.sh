#!/bin/bash

#✔ Une seule fois sur develop
git checkout develop
git fetch upstream
git reset --hard upstream/develop
git push origin develop --force

#✔ (optionnel) sur master
git checkout master
git reset --hard upstream/master
git push origin master --force
