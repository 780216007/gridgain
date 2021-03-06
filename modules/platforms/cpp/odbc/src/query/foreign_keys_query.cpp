/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <ignite/impl/binary/binary_common.h>

#include "ignite/odbc/type_traits.h"
#include "ignite/odbc/connection.h"
#include "ignite/odbc/message.h"
#include "ignite/odbc/query/foreign_keys_query.h"

namespace ignite
{
    namespace odbc
    {
        namespace query
        {
            ForeignKeysQuery::ForeignKeysQuery(diagnostic::Diagnosable& diag, Connection& connection,
                const std::string& primaryCatalog, const std::string& primarySchema,
                const std::string& primaryTable, const std::string& foreignCatalog,
                const std::string& foreignSchema, const std::string& foreignTable) :
                Query(diag, QueryType::FOREIGN_KEYS),
                connection(connection),
                primaryCatalog(primaryCatalog),
                primarySchema(primarySchema),
                primaryTable(primaryTable),
                foreignCatalog(foreignCatalog),
                foreignSchema(foreignSchema),
                foreignTable(foreignTable),
                executed(false),
                columnsMeta()
            {
                using namespace ignite::impl::binary;
                using namespace ignite::odbc::type_traits;

                using meta::ColumnMeta;

                columnsMeta.reserve(14);

                const std::string sch("");
                const std::string tbl("");

                columnsMeta.push_back(ColumnMeta(sch, tbl, "PKTABLE_CAT",   IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "PKTABLE_SCHEM", IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "PKTABLE_NAME",  IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "PKCOLUMN_NAME", IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "FKTABLE_CAT",   IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "FKTABLE_SCHEM", IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "FKTABLE_NAME",  IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "FKCOLUMN_NAME", IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "KEY_SEQ",       IGNITE_TYPE_SHORT));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "UPDATE_RULE",   IGNITE_TYPE_SHORT));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "DELETE_RULE",   IGNITE_TYPE_SHORT));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "FK_NAME",       IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "PK_NAME",       IGNITE_TYPE_STRING));
                columnsMeta.push_back(ColumnMeta(sch, tbl, "DEFERRABILITY", IGNITE_TYPE_SHORT));
            }

            ForeignKeysQuery::~ForeignKeysQuery()
            {
                // No-op.
            }

            SqlResult::Type ForeignKeysQuery::Execute()
            {
                executed = true;

                return SqlResult::AI_SUCCESS;
            }

            const meta::ColumnMetaVector & ForeignKeysQuery::GetMeta() const
            {
                return columnsMeta;
            }

            SqlResult::Type ForeignKeysQuery::FetchNextRow(app::ColumnBindingMap & columnBindings)
            {
                if (!executed)
                {
                    diag.AddStatusRecord(SqlState::SHY010_SEQUENCE_ERROR, "Query was not executed.");

                    return SqlResult::AI_ERROR;
                }

                return SqlResult::AI_NO_DATA;
            }

            SqlResult::Type ForeignKeysQuery::GetColumn(uint16_t columnIdx, app::ApplicationDataBuffer& buffer)
            {
                if (!executed)
                {
                    diag.AddStatusRecord(SqlState::SHY010_SEQUENCE_ERROR, "Query was not executed.");

                    return SqlResult::AI_ERROR;
                }

                return SqlResult::AI_NO_DATA;
            }

            SqlResult::Type ForeignKeysQuery::Close()
            {
                executed = false;

                return SqlResult::AI_SUCCESS;
            }

            bool ForeignKeysQuery::DataAvailable() const
            {
                return false;
            }
            int64_t ForeignKeysQuery::AffectedRows() const
            {
                return 0;
            }

            SqlResult::Type ForeignKeysQuery::NextResultSet()
            {
                return SqlResult::AI_NO_DATA;
            }
        }
    }
}

