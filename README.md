# WSO2 API Manager Migration Resources

[![slack](https://img.shields.io/badge/slack-wso2--apim-blueviolet)](https://join.slack.com/t/wso2-apim/shared_invite/enQtNzEzMzk5Njc5MzM0LTgwODI3NmQ1MjI0ZDQyMGNmZGI4ZjdkZmI1ZWZmMjNkY2E0NmY3ZmExYjkxYThjNzNkOTU2NWJmYzM4YzZiOWU?)
[![StackOverflow](https://img.shields.io/badge/stackoverflow-wso2am-orange)](https://stackoverflow.com/tags/wso2-am/)
[![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fwso2.org%2Fjenkins%2Fjob%2Fapim-extensions%2Fjob%2Fapim-migration-resources_4.0.0%2F)](https://wso2.org/jenkins/job/apim-extensions/job/apim-migration-resources_4.0.0/)

---

This repository is used to maintain the migration client for WSO2 API Manager (WSO2 API-M).
The migration client is used when users of older versions of the WSO2 API manager migrates to a newer version.
In addition, pre-validators are provided to verify the integrity of the data in the older version before migration, to avoid failures at migration time.

To migrate to the latest version:
- Visit the [latest documentation](https://apim.docs.wso2.com/en/latest/install-and-setup/upgrading-wso2-api-manager/upgrading-guidelines/)
- Follow the given instructions

Make sure to refer to the documentation of the version you want to migrate to, if you are not migrating to the latest version.

## Contributing to WSO2 API-M Migration Resources

As an open source project, WSO2 API-M Migration Resources welcomes contributions from the community. To start contributing, read these contribution guidelines for information on how you should go about contributing to our project.

1. Accept the Contributor License Agreement (CLA)

   You need to Accept the Contributor License Agreement (CLA) when prompted by a GitHub email notification after sending your first Pull Request (PR). Subsequent PRs will not require CLA acceptance.

   If the CLA gets changed for some (unlikely) reason, you will be presented with the new CLA text after sending your first PR after the change.

2. Fork this repository, make your changes, and send in a pull request (PR). Make sure you are contributing to the correct branch (for example, if your change is relevant to WSO2 API-M 4.0.0 migration client, you should commit your changes to the 4.0.0 branch).

3. Send multiple pull requests to all the relevant branches.

   If your change is relevant to the latest API-M release, please send your change to the respective latest API-M release branch and the master branch, which is the branch used for the migration client of the upcoming API-M release, as well.

   For example, if the latest API-M release is 4.1.0, and if your change is relevant to API-M 4.1.0 and 4.0.0 send PRs to the 4.0.0, 4.1.0 and the master branches.

Check the issue tracker for open issues that interest you. We look forward to receiving your contributions.

## License

Licenses this source under the Apache License, Version 2.0 ([LICENSE](LICENSE)), You may not use this file except in compliance with the License.