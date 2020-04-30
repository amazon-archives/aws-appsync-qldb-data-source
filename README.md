# AWS AppSync + Amazon QLDB

[Amazon QLDB](https://aws.amazon.com/qldb/) is a purpose-built database for use cases that require an authoritative data source. QLDB maintains a complete, immutable history of all changes committed to the database (referred to as a "ledger"). QLDB fits well in finance, eCommerce, inventory, government, and numerous other applications.

Pairing QLDB with services such as [AWS AppSync](https://aws.amazon.com/appsync/) allows AWS customers to safely expose both data and the history of that data for mobile applications, websites, or a data lake.

This sample project accompanies a series of blog posts on the [AWS Database Blog](https://aws.amazon.com/blogs/database/):

* [Part 1: Building a GraphQL interface to Amazon QLDB with AWS AppSync](https://aws.amazon.com/blogs/database/)
* [Part 2: Building a GraphQL interface to Amazon QLDB with AWS AppSync](https://aws.amazon.com/blogs/database/)

## Getting Started

To get started with aws-appsync-qldb-sample:

### Prerequisites

Select an AWS Region that supports all required services, including Amazon QLDB, AWS AppSync, and AWS Lambda. Amazon QLDB is available today in US East (Ohio), US East (N. Virginia), US West (Oregon), Asia Pacific (Tokyo), and EU (Ireland). AWS AppSync is supported in all of these same Regions.

This project requires the following prerequisites:

* [AWS Account](https://aws.amazon.com/account/)
* [Java 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/what-is-corretto-11.html)
* [Maven](https://maven.apache.org/install.html)
* [AWS SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)

> Note: AWS offers a [Free Tier](https://aws.amazon.com/free/) for many services, though not all used in this project are part of the Free Tier.

## Loading Sample Data

To demonstrate the integration between AWS AppSync and QLDB, we will use the sample data set provided as part of the Amazon QLDB Tutorial. The tutorial is available for [Java](https://docs.aws.amazon.com/qldb/latest/developerguide/getting-started.java.tutorial.html), [Python](https://docs.aws.amazon.com/qldb/latest/developerguide/getting-started.python.tutorial.html), and [Node.js](https://docs.aws.amazon.com/qldb/latest/developerguide/getting-started.nodejs.tutorial.html). The following directions use the Node.js version, but use whichever you are most comfortable.

As part of the QLDB Tutorial and this sample, we will use a Department of Motor Vehicles (DMV) data set that manages vehicle ownership. This data is fictional.

## Deployment

Next, we can deploy the AWS AppSync API for the DMV sample and the Lambda function included in this project to integrate QLDB as an AppSync Data Source. AWS SAM makes this easy:

``` bash
sam build

sam deploy --guided
```

In response to the prompts, enter the following values:

* Stack Name [sam-app]: __aws-appsync-qldb-data-source__
* AWS Region [us-east-1]: __your region of choice__
* Confirm changes before deploy [y/N]: __Y__
* Allow SAM CLI IAM role creation [Y/n]: __Y__
* Save arguments to samconfig.toml [Y/n]: __Y__

Press __Enter__ to start the deployment.

Review the resources that will be created as part of this stack. When prompted, enter "y" to deploy the changeset:

* Deploy this changeset? [y/N]: __Y__

When deployment has finished, make note of the stack outputs. These will include the AppSync API Endpoint and an API Key. For example:

```
-------------------------------------------------------------------------------------------------------
OutputKey-Description                                     OutputValue
-------------------------------------------------------------------------------------------------------
ApiKey - AppSync API Key                                  ab1-1234567890
ApiEndpoint - AppSync API Endpoint                        https://abc1234567890.appsync-api.us-east-1.amazonaws.com/graphql
-------------------------------------------------------------------------------------------------------
```

Sample data will be automatically loaded in your QLDB Ledger. This project uses the same sample data set provided by the [Amazon QLDB Java Sample Application](https://docs.aws.amazon.com/qldb/latest/developerguide/runningsamples.html#runningsamples.cfn-ac9.step-3).

## Testing

Let's walk through a typical interaction with the DMV's new GraphQL-powered API. While an end user would generally not work directly with the API (there would be a nice user interface in front), this project is focused purely on the backend.

To interact with the AppSync API, you can use a GraphQL IDE such as [Insomnia](https://insomnia.rest/graphql/), [Postman](https://learning.postman.com/docs/postman/sending-api-requests/graphql/) or the the Query Editor in the AppSync Console.

### Working at the DMV

At the start of our day, two people approach the DMV. Alexis Pena is selling her Ducati Monster 1200 to Brent Logan. To confirm that Alexis is the legal owner of the Ducati, we can query for the list of vehicles registered to her:

``` graphql
query VehiclesByOwner {
  vehiclesByOwner(govId:"744 849 301") {
    VIN
    Make
    Model
  }
}
```

The result of the query includes the Ducati and the expected VIN number:

``` json
{
  "data": {
    "vehiclesByOwner": [
      {
        "VIN": "3HGGK5G53FM761765",
        "Make": "Ducati",
        "Model": "Monster 1200"
      }
    ]
  }
}
```

After confirming that Alexis and Brent have signed the proper paperwork, we can update the ownership of the Ducati in the managed ledger database used by the DMV:

``` graphql
mutation UpdatePrimaryOwner{
  updateVehicleOwner(
    vin:"3HGGK5G53FM761765",
    govId:"LOGANB486CG"
  )
}
```

The mutation should return a `true` result and no errors. To confirm the change in ownership, we can query the vehicle's registration data:

``` graphql
query VehicleRegistration {
  getVehicleRegistration(vin: "3HGGK5G53FM761765") {
    ValidFromDate
    ValidToDate
    Owners {
      PrimaryOwner {
        FirstName
        LastName
        GovId
      }
    }
  }
}
```

The result shows that Brent is now the owner and the registration is valid:

``` json
{
  "data": {
    "getVehicleRegistration": {
      "ValidFromDate": "2011-03-17T00:00:00.000Z",
      "ValidToDate": "2021-03-24T00:00:00.000Z",
      "Owners": {
        "PrimaryOwner": {
          "FirstName": "Brent",
          "LastName": "Logan",
          "GovId": "LOGANB486CG"
        }
      }
    }
  }
}
```

One of the primary benefits of Amazon QLDB is the ease of accessing the change history of data in the ledger. We can also query for the history of the Ducati's ownership (this query might take a few seconds):

``` graphql
query GetVehicleHistory {
  getOwnershipHistory(vin:"3HGGK5G53FM761765") {
    version
    txTime
    data {
      ... on VehicleRegistration {
        Owners {
          PrimaryOwner {
            FirstName
            LastName
            GovId
          }
        }
      }
    }
  }
}
```

The result shows the change in ownership from Alexis to Brent as well as the data of the transaction that changed this data:

``` json
{
  "data": {
    "getOwnershipHistory": [
      {
        "version": 0,
        "txTime": "2020-01-30T16:11:00.549Z",
        "data": {
          "Owners": {
            "PrimaryOwner": {
              "FirstName": "Alexis",
              "LastName": "Pena",
              "GovId": "744 849 301"
            }
          }
        }
      },
      {
        "version": 1,
        "txTime": "2020-02-05T22:17:36.262Z",
        "data": {
          "Owners": {
            "PrimaryOwner": {
              "FirstName": "Brent",
              "LastName": "Logan",
              "GovId": "LOGANB486CG"
            }
          }
        }
      }
    ]
  }
 }
```

# Exploring the Integration Data Source

The integration between AppSync and QLDB makes use of a Lambda function that supports arbitrary QLDB queries. Queries can be configured on a per-resolver basis to support a wide variety of functionality. The primary handler function can be found in [App.java](./QLDBIntegrationFunction/src/main/java/qldb/App.java).

> To date, the integration function has been tested on a limited number of use cases. Some, such as those that require non-String parameters, may not work yet.

GraphQL queries and mutations are defined using AppSync Resolvers. To integrate with QLDB, the request mapping contains the appropriate envelope to invoke a Lambda function and passes a payload containing one or more [PartiQL](https://aws.amazon.com/blogs/opensource/announcing-partiql-one-query-language-for-all-your-data/) queries to be executed on the QLDB ledger along with associated arguments. For example, to query the `Vehicle` table for a particular entry, the GraphQL query coud look like the following:

``` graphql
type Query {
  getVehicle(vin:String!): Vehicle
}
```

The request mapping for the resolver associated with this query includes the query itself and passes the appropriate argument from the GraphQL query:

``` json
{
  "version": "2017-02-28",
  "operation": "Invoke", 
  "payload": {
    "action": "Query",
    "payload": [
      {
        "query": "SELECT * FROM Vehicle AS t WHERE t.VIN = ?",
        "args": [ "$ctx.args.vin" ]
      }
    ]
  }
}
```

The PartiQL query above is drawn directly from the QLDB Tutorial tutorial noted above. The result includes all details about the vehicle with the specified VIN number.

Arguments are filled by index order provided.

Currently, the response from the integration function includes a String representation of the result of the PartiQL query. We use the response mapping to parse that result and return JSON to the caller:

``` json
#if ($ctx.result.error)
  $utils.error($ctx.result.error)
#else
  #set( $result = $util.parseJson($ctx.result.result) )
  $util.toJson($result[0])
#end
```

## Executing Multiple Queries in Sequence

Because we often need to execute multiple PartiQL queries in series, the integration function supports passing more than one query in a single request. All queries passed in a request are executed as part of a single QLDB transaction.

> Note: AppSync Pipeline Resolvers could also be used, but this approach had some latency benefits.

When executing multiple queries, you can also use [JMESPath](http://jmespath.org) expressions to retrieve data from the *previous* result. This allows for common use cases such as looking up a QLDB Document ID to perform a subsequent query, such as in the following example.

To utilize JMESPath expressions, include it in the `args` array and prepend the JMESPath expression with `$.`. In the following example request mapping, the first query in the resolver retrieve the unique document ID for the `Person` identified by the passed `govId`. The returned result from that query looks like: `{ [ 'id': '1234' ] }`. Using JMESPath, we can get the `id` attribute of the first entry in the array using an expression such as `[0].id`. Here, we prepend with `$.` to identify that argument as JMESPath in the resolver, completing the example:

``` json
{
  "version": "2017-02-28",
  "operation": "Invoke",
  "payload": {
    "action": "Query",
    "payload": [
      {
        "query": "SELECT id FROM Person AS t BY id WHERE t.GovId = ?",
        "args": [ "$ctx.args.govId" ]
      },
      {
        "query": "SELECT Vehicle FROM Vehicle INNER JOIN VehicleRegistration AS r ON Vehicle.VIN = r.VIN WHERE r.Owners.PrimaryOwner.PersonId = ?",
        "args": [ "$.[0].id" ]
      }
    ]
  }
}
```

Both of the PartiQL queries used in this example are again pulled directly from the QLDB Tutorial. The result of this query is an array of `VehicleRegistration` objects associated with the `Person` identified by the `govId` argument passed to the GraphQL query.

It is important to remember that the JMESPath expression will apply only to the result of the preceeding query. If you need to execute more than one query to collect inputs, for now, consider using an AppSync Pipeline Resolver.

# Cleaning Up

To delete the project, delete the serverless application and QLDB ledger:

``` bash
aws cloudformation delete-stack --stack-name aws-appsync-qldb-data-source
```

# Authors
* **Josh Kahn**- initial work
