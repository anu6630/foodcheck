import type { DataSource } from "../../domain/dataSource.js";
import type { IDataSourceRepository } from "../../domain/interfaces.js";
import { listDataSources } from "../../infrastructure/dataSources.js";

export class PgDataSourceRepository implements IDataSourceRepository {
  listAll(): Promise<DataSource[]> {
    return listDataSources();
  }
}
