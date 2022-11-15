import React from 'react';

import { Link } from 'react-router-dom';

import {
  Button,
  HTMLTable,
  Toaster,
} from '@blueprintjs/core';
import Container from 'react-bootstrap/Container';
import { getComponentForType } from './ComponentMap'

import copy from 'copy-to-clipboard';

const toaster = Toaster.create();

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

  let dataFlat: any[] = []

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


  async function copy() {
    if (navigator.clipboard) {
      const vals = dataFlat.map((d: any) => {
        const joined = columns.map((c) => (d[c.name])).join('\t');
        return `${joined}\n`;
      });

      await navigator.clipboard.write([
        new ClipboardItem({'text/plain': new Blob(vals, {type: 'text/plain'})})
      ]);

      toaster.show({
        message: `Copied ${dataFlat.length} values to clipboard`,
        intent: "success",
        timeout: 3000
      })
    } else {
      toaster.show({
        message: "Failed to copy",
        intent: "danger",
        timeout: 3000
      })
    }
  }

  return <Container>
    { props.returnTime && <pre>Returned in {props.returnTime} ms</pre> }
    { props.finishTime && <pre>Finished in {props.finishTime} ms</pre> }
    <pre>{Math.min(props.size, total)} of {total}</pre>
    <Button icon="clipboard" onClick={copy}/>
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
