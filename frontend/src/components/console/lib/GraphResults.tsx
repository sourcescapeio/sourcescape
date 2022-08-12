import React from 'react';

import { Link } from 'react-router-dom';

import {
  Button,
  HTMLTable,
} from '@blueprintjs/core';
import Container from 'react-bootstrap/Container';
import { getComponentForType } from './ComponentMap'

export function GraphResultsComponent(props: {
  columns: any[],
  data: any[],
  isDiff?: boolean,
  diffData?: any,
  size: number,
  returnTime?: number | null,
  finishTime?: number | null,
  getMore: () => void
}) {
  const { columns, isDiff } = props;

  if (!columns) {
    return <pre>Loading...</pre>;
  }

  const columnsRendered = columns.map((c, idx) => (<th>
    {c.name}
  </th>));

  let dataFlat = []

  if (isDiff) {
    const diffData = props.diffData || {};
    // woefully inefficient
    dataFlat = Object.keys(props.diffData).sort().map((k) => (diffData[k]))
  } else {
    dataFlat = props.data || [];
  }

  const dataRendered = dataFlat.slice(0, props.size).map((d) => {
    const guts = columns.map((c) => {
      const r = getComponentForType(c.type);
      return d[c.name] ? <td>{r(d[c.name])}</td> : <td>-</td>
    });

    return <tr>{guts}</tr>;
  });

  const total = isDiff ? Object.keys(props.diffData).length : props.data.length;

  return <Container>
    { props.returnTime && <pre>Returned in {props.returnTime} ms</pre> }
    { props.finishTime && <pre>Finished in {props.finishTime} ms</pre> }
    <pre>{Math.min(props.size, total)} of {total}</pre>
    <HTMLTable>
      <thead>
        <tr>{columnsRendered}</tr>
      </thead>
      <tbody>
        {dataRendered}
      </tbody>
    </HTMLTable>
    <Button icon="plus" onClick={props.getMore} />
  </Container>
}
