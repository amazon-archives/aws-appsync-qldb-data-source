from __future__ import print_function
from crhelper import CfnResource
from pyqldb.driver.pooled_qldb_driver import PooledQldbDriver

from lib.constants import Constants
from lib.sample_data import convert_object_to_ion, SampleData, get_document_ids_from_dml_results
from lib.connect_to_ledger import create_qldb_session

import logging
import time

logger = logging.getLogger(__name__)
helper = CfnResource(json_logging=False, log_level='DEBUG', boto_level='CRITICAL')

try:
  pass
except Exception as e:
  helper.init_failure(e)

@helper.create
def create(event, context):
  qldb_ledger = event['ResourceProperties']['QldbLedger']
  logger.info('CREATE: %s', qldb_ledger)

  try:
    with create_qldb_session(ledger_name=qldb_ledger) as session:
      session.execute_lambda(lambda x: create_table(x, Constants.DRIVERS_LICENSE_TABLE_NAME) and
                            create_table(x, Constants.PERSON_TABLE_NAME) and
                            create_table(x, Constants.VEHICLE_TABLE_NAME) and
                            create_table(x, Constants.VEHICLE_REGISTRATION_TABLE_NAME),
                            lambda retry_attempt: logger.info('Retrying due to OCC conflict...'))
      logger.info('Tables created successfully.')
      
      time.sleep(10)

      session.execute_lambda(lambda x: create_index(x, Constants.PERSON_TABLE_NAME, Constants.GOV_ID_INDEX_NAME)
                            and create_index(x, Constants.VEHICLE_TABLE_NAME, Constants.VEHICLE_VIN_INDEX_NAME)
                            and create_index(x, Constants.VEHICLE_REGISTRATION_TABLE_NAME, Constants.LICENSE_PLATE_NUMBER_INDEX_NAME)
                            and create_index(x, Constants.VEHICLE_REGISTRATION_TABLE_NAME, Constants.VEHICLE_VIN_INDEX_NAME)
                            and create_index(x, Constants.DRIVERS_LICENSE_TABLE_NAME, Constants.PERSON_ID_INDEX_NAME)
                            and create_index(x, Constants.DRIVERS_LICENSE_TABLE_NAME, Constants.LICENSE_NUMBER_INDEX_NAME),
                            lambda retry_attempt: logger.info('Retrying due to OCC conflict...'))
      logger.info('Indexes created successfully.')
      
      time.sleep(10)

      session.execute_lambda(lambda executor: update_and_insert_documents(executor),
                            lambda retry_attempt: logger.info('Retrying due to OCC conflict...'))
      logger.info('Documents inserted successfully!')
  except Exception as err:
    logger.exception('Errors creating resources.')
    logger.exception(err)

  return None

@helper.update
def update(event, context):
  logger.info('UPDATE')
  return True

@helper.delete
def delete(event, context):
  logger.info('DELETE')
  return True

### main handler
def handler(event, context):
  helper(event, context)


def create_table(transaction_executor, table_name):
  logger.info("Creating the '{}' table...".format(table_name))
  statement = 'CREATE TABLE {}'.format(table_name)
  cursor = transaction_executor.execute_statement(statement)
  logger.info('{} table created successfully.'.format(table_name))
  return len(list(cursor))

def create_index(transaction_executor, table_name, index_attribute):
  logger.info("Creating index on '{}'...".format(index_attribute))
  statement = 'CREATE INDEX on {} ({})'.format(table_name, index_attribute)
  cursor = transaction_executor.execute_statement(statement)
  return len(list(cursor))

def update_person_id(document_ids):
    new_drivers_licenses = SampleData.DRIVERS_LICENSE.copy()
    new_vehicle_registrations = SampleData.VEHICLE_REGISTRATION.copy()
    for i in range(len(SampleData.PERSON)):
        drivers_license = new_drivers_licenses[i]
        registration = new_vehicle_registrations[i]
        drivers_license.update({'PersonId': str(document_ids[i])})
        registration['Owners']['PrimaryOwner'].update({'PersonId': str(document_ids[i])})
    return new_drivers_licenses, new_vehicle_registrations


def insert_documents(transaction_executor, table_name, documents):
    logger.info('Inserting some documents in the {} table...'.format(table_name))
    statement = 'INSERT INTO {} ?'.format(table_name)
    cursor = transaction_executor.execute_statement(statement, convert_object_to_ion(documents))
    list_of_document_ids = get_document_ids_from_dml_results(cursor)

    return list_of_document_ids


def update_and_insert_documents(transaction_executor):
    list_ids = insert_documents(transaction_executor, Constants.PERSON_TABLE_NAME, SampleData.PERSON)

    logger.info("Updating PersonIds for 'DriversLicense' and PrimaryOwner for 'VehicleRegistration'...")
    new_licenses, new_registrations = update_person_id(list_ids)

    insert_documents(transaction_executor, Constants.VEHICLE_TABLE_NAME, SampleData.VEHICLE)
    insert_documents(transaction_executor, Constants.VEHICLE_REGISTRATION_TABLE_NAME, new_registrations)
    insert_documents(transaction_executor, Constants.DRIVERS_LICENSE_TABLE_NAME, new_licenses)