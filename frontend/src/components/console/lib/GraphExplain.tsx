import React from 'react';

import {
  HTMLTable,
} from '@blueprintjs/core';

import _ from 'lodash';

const KEYS = [
  // 'left-pre',
  // 'right-pre',
  'buffer',
  // 'left',
  // 'right',
  // 'join',
  // 'emit',
];

export function GraphExplainComponent(props: {
  data: any[],
  columns: any[]
  // nodes: any[],
  // edges: any[]
}) {
  // this.props.nodes
  // this.props.data
  const dataMap = _.groupBy(props.data || [], (d) => (d.nodeKey + ":" + d.direction))

  //
  return <div>
      <HTMLTable striped={true}>
      {(props.columns || []).map((node) => (
        <th style={{fontSize:30}}>{node}</th>
      ))}
      <tr>
        {(props.columns || []).map((node) => {
          const items = KEYS.map((k) => {
            const data = dataMap[`${node}:${k}`] || [];

            return <div>
              <b>{k.toUpperCase()}</b>
              {data.map((d) => (
                <pre>{JSON.stringify(d.data, null, 2)}</pre>
              ))}
            </div>
          });

          return <td>
            <div style={{display: 'flex'}}>
              {items}              
            </div>
          </td>
        })}
      </tr>
    </HTMLTable>
  </div>
}
